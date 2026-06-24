"""短剧大全 API 服务 - 独立数据库，与抢购系统完全隔离"""
import sqlite3, hashlib, secrets, time, json
from datetime import date
from contextlib import contextmanager
from fastapi import FastAPI, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional

app = FastAPI(title="短剧大全 API")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

DB = "drama.db"

@contextmanager
def get_db():
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
    finally:
        conn.close()

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
            CREATE TABLE IF NOT EXISTS dramas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                cover TEXT DEFAULT '',
                description TEXT DEFAULT '',
                category TEXT DEFAULT '',
                episode_count INTEGER DEFAULT 0,
                score REAL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS episodes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                drama_id INTEGER REFERENCES dramas(id),
                title TEXT NOT NULL,
                video_url TEXT DEFAULT '',
                duration INTEGER DEFAULT 0,
                "order" INTEGER DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER REFERENCES users(id),
                device_id TEXT DEFAULT '',
                drama_id INTEGER,
                episode_id INTEGER,
                progress INTEGER DEFAULT 0,
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
    seed_data()

def seed_data():
    with get_db() as db:
        count = db.execute("SELECT COUNT(*) FROM dramas").fetchone()[0]
        if count > 0:
            return
        dramas = [
            ("霸总爱上我", "#FF6B6B", "甜宠霸总短剧", "甜宠", 80, 8.5),
            ("重生之豪门千金", "#4ECDC4", "重生复仇爽剧", "都市", 100, 9.1),
            ("穿越古代当王妃", "#FFD93D", "古装穿越甜宠", "古装", 60, 7.8),
            ("闪婚后被大佬宠上天", "#6BCB77", "现代甜宠剧", "甜宠", 50, 8.2),
            ("逆袭从今天开始", "#45B7D1", "都市逆袭爽剧", "都市", 90, 8.9),
            ("神医下山", "#DDA0DD", "神医都市剧", "都市", 70, 8.0),
            ("穿越之女帝驾到", "#FF8C42", "女尊穿越剧", "古装", 55, 7.5),
            ("总裁的替身新娘", "#98D8C8", "替身甜宠文", "甜宠", 65, 8.7),
            ("特工王妃太嚣张", "#F7DC6F", "古装特工", "古装", 80, 8.3),
            ("回到民国当少帅", "#BB8FCE", "民国言情", "最新", 45, 8.6),
        ]
        for d in dramas:
            db.execute(
                "INSERT INTO dramas(title,cover,description,category,episode_count,score) VALUES(?,?,?,?,?,?)",
                d
            )
        db.commit()
        drama_ids = [row[0] for row in db.execute("SELECT id FROM dramas").fetchall()]
        sample_videos = [
            "http://vjs.zencdn.net/v/oceans.mp4",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4",
        ]
        for did in drama_ids:
            ep_count = db.execute("SELECT episode_count FROM dramas WHERE id=?", (did,)).fetchone()[0]
            for ep in range(1, min(ep_count + 1, 6)):
                video_url = sample_videos[(ep - 1) % len(sample_videos)]
                db.execute(
                    "INSERT INTO episodes(drama_id,title,video_url,duration,\"order\") VALUES(?,?,?,?,?)",
                    (did, f"第{ep}集", video_url, 3, ep)
                )
        db.commit()

class LoginReq(BaseModel):
    username: str
    password: str

class HistoryReq(BaseModel):
    drama_id: int = 0
    episode_id: int = 0
    device_id: str = ""

def get_user_from_token(db, auth: str):
    if not auth or not auth.startswith("Bearer "):
        return None
    token = auth[7:]
    return db.execute("SELECT * FROM users WHERE token=?", (token,)).fetchone()

@app.on_event("startup")
def startup():
    init_db()

# ---- 短剧 API ----
@app.get("/api/drama/list")
def drama_list(category: str = ""):
    with get_db() as db:
        if category:
            rows = db.execute("SELECT * FROM dramas WHERE category=? ORDER BY id DESC", (category,)).fetchall()
        else:
            rows = db.execute("SELECT * FROM dramas ORDER BY id DESC").fetchall()
        return {"status": "success", "data": [dict(r) for r in rows]}

@app.get("/api/drama/detail/{drama_id}")
def drama_detail(drama_id: int):
    with get_db() as db:
        drama = db.execute("SELECT * FROM dramas WHERE id=?", (drama_id,)).fetchone()
        if not drama:
            raise HTTPException(404, "短剧不存在")
        episodes = db.execute("SELECT * FROM episodes WHERE drama_id=? ORDER BY \"order\"", (drama_id,)).fetchall()
        return {"status": "success", "data": {"drama": dict(drama), "episodes": [dict(e) for e in episodes]}}

# ---- 用户 API ----
@app.post("/api/user/register")
def register(req: LoginReq):
    with get_db() as db:
        if db.execute("SELECT 1 FROM users WHERE username=?", (req.username,)).fetchone():
            return {"status": "error", "message": "用户名已存在"}
        h = hashlib.sha256(req.password.encode()).hexdigest()
        token = secrets.token_hex(16)
        db.execute("INSERT INTO users(username,password_hash,token) VALUES(?,?,?)", (req.username, h, token))
        db.commit()
        user = db.execute("SELECT * FROM users WHERE username=?", (req.username,)).fetchone()
        return {"status": "success", "token": token, "user": {"id": user["id"], "username": user["username"], "points": 0}}

@app.post("/api/user/login")
def login(req: LoginReq):
    with get_db() as db:
        h = hashlib.sha256(req.password.encode()).hexdigest()
        user = db.execute("SELECT * FROM users WHERE username=? AND password_hash=?", (req.username, h)).fetchone()
        if not user:
            return {"status": "error", "message": "用户名或密码错误"}
        token = secrets.token_hex(16)
        db.execute("UPDATE users SET token=? WHERE id=?", (token, user["id"]))
        db.commit()
        return {"status": "success", "token": token, "user": {"id": user["id"], "username": user["username"], "points": user["points"]}}

@app.get("/api/user/info")
def user_info(authorization: str = Header("")):
    with get_db() as db:
        user = get_user_from_token(db, authorization)
        if not user:
            return {"status": "error", "message": "未登录"}
        today = date.today().isoformat()
        signed = db.execute("SELECT 1 FROM checkins WHERE user_id=? AND date=?", (user["id"], today)).fetchone()
        return {"status": "success", "user": {"id": user["id"], "username": user["username"], "points": user["points"], "signed_today": signed is not None}}

@app.post("/api/user/checkin")
def checkin(authorization: str = Header("")):
    with get_db() as db:
        user = get_user_from_token(db, authorization)
        if not user:
            return {"status": "error", "message": "请先登录"}
        today = date.today().isoformat()
        if db.execute("SELECT 1 FROM checkins WHERE user_id=? AND date=?", (user["id"], today)).fetchone():
            return {"status": "error", "message": "今天已签到"}
        pts = 10
        db.execute("INSERT INTO checkins(user_id,date,points_earned) VALUES(?,?,?)", (user["id"], today, pts))
        db.execute("UPDATE users SET points=points+? WHERE id=?", (pts, user["id"]))
        db.commit()
        new_pts = db.execute("SELECT points FROM users WHERE id=?", (user["id"],)).fetchone()[0]
        return {"status": "success", "points_earned": pts, "total_points": new_pts, "message": "签到成功"}

# ---- 历史 API ----
@app.get("/api/history/list")
def history_list(authorization: str = Header("")):
    with get_db() as db:
        user = get_user_from_token(db, authorization)
        if user:
            rows = db.execute("""
                SELECT h.*, d.title as drama_title, e.title as episode_title, d.cover
                FROM history h JOIN dramas d ON h.drama_id=d.id
                LEFT JOIN episodes e ON h.episode_id=e.id
                WHERE h.user_id=? ORDER BY h.watched_at DESC LIMIT 50
            """, (user["id"],)).fetchall()
        else:
            return {"status": "success", "data": []}
        return {"status": "success", "data": [dict(r) for r in rows]}

@app.post("/api/history/save")
def history_save(req: HistoryReq, authorization: str = Header("")):
    with get_db() as db:
        user = get_user_from_token(db, authorization)
        uid = user["id"] if user else 0
        db.execute(
            "INSERT INTO history(user_id,device_id,drama_id,episode_id) VALUES(?,?,?,?)",
            (uid, req.device_id, req.drama_id, req.episode_id)
        )
        db.commit()
        return {"status": "success"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
