"""短剧大全 API 服务 - 百度短剧(52api.cn) + 独立用户数据库"""
import sqlite3, hashlib, secrets, requests as _requests
from datetime import date
from contextlib import contextmanager
from fastapi import FastAPI, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel

app = FastAPI(title="短剧大全 API")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

DB = "drama.db"
API_KEY = "mWqvYloCyXJjhm3kdif9VgZYak"
API_URL = "https://www.52api.cn/api/bd_duanju"

@contextmanager
def get_db():
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    try: yield conn
    finally: conn.close()

def init_db():
    with get_db() as db:
        db.executescript("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                token TEXT,
                points INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER REFERENCES users(id),
                device_id TEXT DEFAULT '',
                drama_id TEXT DEFAULT '',
                episode_id TEXT DEFAULT '',
                watched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS checkins (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER REFERENCES users(id),
                date TEXT NOT NULL,
                points_earned INTEGER DEFAULT 10
            );
        """)
        db.commit()

def _call_api(type_: str, **kwargs) -> dict:
    """调用52api.cn百度短剧"""
    params = {"key": API_KEY, "type": type_}
    params.update(kwargs)
    try:
        if type_ == "search":
            r = _requests.post(API_URL, json=params, timeout=30, proxies={"http": None, "https": None})
        else:
            r = _requests.get(API_URL, params=params, timeout=30, proxies={"http": None, "https": None})
        return r.json()
    except Exception as e:
        return {"code": -1, "msg": str(e), "data": None}

class LoginReq(BaseModel):
    username: str
    password: str

def get_user_from_token(db, auth: str):
    if not auth or not auth.startswith("Bearer "): return None
    return db.execute("SELECT * FROM users WHERE token=?", (auth[7:],)).fetchone()

@app.on_event("startup")
def startup():
    init_db()

# ---- 短剧 API (百度短剧) ----
@app.get("/api/drama/list")
def drama_list(category: str = ""):
    kw = category if category else "热播"
    resp = _call_api("search", keyword=kw, page=1)
    if resp.get("code") == 200:
        data = []
        for item in resp.get("data", []):
            data.append({
                "id": item.get("id", ""),
                "title": item.get("title", ""),
                "cover": item.get("cover", ""),
                "description": "",
                "category": "",
                "episode_count": item.get("totalChapterNum", 0),
                "score": item.get("score", "0"),
            })
        return JSONResponse({"status": "success", "data": data})
    return JSONResponse({"status": "error", "data": [], "msg": f"api_code={resp.get('code')}"})

@app.get("/api/drama/detail/{drama_id}")
def drama_detail(drama_id: str):
    resp = _call_api("detail", id=drama_id)
    if resp.get("code") == 200:
        data = resp.get("data", {})
        episodes = []
        for ep in data.get("lists", []):
            episodes.append({
                "video_id": ep.get("video_id", ""),
                "title": ep.get("title", ""),
                "order": ep.get("chapter_no", ep.get("index", 0)),
                "duration": 180,
            })
        return JSONResponse({
            "status": "success",
            "data": {
                "drama": {
                    "id": data.get("id", drama_id),
                    "title": data.get("title", ""),
                    "cover": data.get("cover", ""),
                    "intro": data.get("intro", ""),
                    "type": "",
                    "score": data.get("score", "0"),
                    "episode_count": len(episodes),
                },
                "episodes": episodes
            }
        })
    return JSONResponse({"status": "error", "data": None, "msg": f"api_code={resp.get('code')}"})

@app.get("/api/drama/video/{video_id}")
def drama_video(video_id: str):
    resp = _call_api("video", video_id=video_id)
    if resp.get("code") == 200:
        vl = resp.get("data", {}).get("video_lists", [])
        url = vl[0].get("url", "") if vl else ""
        return JSONResponse({"status": "success", "data": {"video_url": url}})
    return JSONResponse({"status": "error", "data": None})

# ---- 用户 API ----
@app.post("/api/drama/user/register")
def drama_register(req: LoginReq):
    with get_db() as db:
        if db.execute("SELECT 1 FROM users WHERE username=?", (req.username,)).fetchone():
            return {"status": "error", "message": "用户名已存在"}
        h = hashlib.sha256(req.password.encode()).hexdigest()
        token = secrets.token_hex(16)
        db.execute("INSERT INTO users(username,password_hash,token) VALUES(?,?,?)", (req.username, h, token))
        db.commit()
        u = db.execute("SELECT * FROM users WHERE username=?", (req.username,)).fetchone()
        return {"status": "success", "token": token, "user": {"id": u["id"], "username": u["username"], "points": 0}}

@app.post("/api/drama/user/login")
def drama_login(req: LoginReq):
    with get_db() as db:
        h = hashlib.sha256(req.password.encode()).hexdigest()
        u = db.execute("SELECT * FROM users WHERE username=? AND password_hash=?", (req.username, h)).fetchone()
        if not u: return {"status": "error", "message": "用户名或密码错误"}
        token = secrets.token_hex(16)
        db.execute("UPDATE users SET token=? WHERE id=?", (token, u["id"]))
        db.commit()
        return {"status": "success", "token": token, "user": {"id": u["id"], "username": u["username"], "points": u["points"]}}

@app.get("/api/drama/user/info")
def drama_user_info(authorization: str = Header("")):
    with get_db() as db:
        u = get_user_from_token(db, authorization)
        if not u: return {"status": "error", "message": "未登录"}
        today = date.today().isoformat()
        signed = db.execute("SELECT 1 FROM checkins WHERE user_id=? AND date=?", (u["id"], today)).fetchone()
        return {"status": "success", "user": {"id": u["id"], "username": u["username"], "points": u["points"], "signed_today": signed is not None}}

@app.post("/api/drama/user/checkin")
def drama_checkin(authorization: str = Header("")):
    with get_db() as db:
        u = get_user_from_token(db, authorization)
        if not u: return {"status": "error", "message": "请先登录"}
        today = date.today().isoformat()
        if db.execute("SELECT 1 FROM checkins WHERE user_id=? AND date=?", (u["id"], today)).fetchone():
            return {"status": "error", "message": "今天已签到"}
        pts = 10
        db.execute("INSERT INTO checkins(user_id,date,points_earned) VALUES(?,?,?)", (u["id"], today, pts))
        db.execute("UPDATE users SET points=points+? WHERE id=?", (pts, u["id"]))
        db.commit()
        new_pts = db.execute("SELECT points FROM users WHERE id=?", (u["id"],)).fetchone()[0]
        return {"status": "success", "points_earned": pts, "total_points": new_pts}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
