"""Small same-origin interest-list service. Stores email addresses locally, never logs them."""
import os
import re
import sqlite3
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock
from urllib.parse import parse_qs

DB_PATH = os.environ.get('INTEREST_DB', '/data/interest.sqlite3')
SITE_ORIGIN = os.environ.get('SITE_ORIGIN', 'https://egauge.majjix.com')
EMAIL = re.compile(r'^[^\s@<>]{1,64}@[^\s@<>]{1,189}$')
last_attempt = {}
lock = Lock()


def database():
    connection = sqlite3.connect(DB_PATH, timeout=10)
    connection.execute('PRAGMA busy_timeout=10000')
    return connection


def initialize():
    with database() as db:
        db.execute("CREATE TABLE IF NOT EXISTS interest (email TEXT PRIMARY KEY COLLATE NOCASE, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, consent_text TEXT NOT NULL, deposit_interest INTEGER NOT NULL DEFAULT 0, vip_interest INTEGER NOT NULL DEFAULT 0, roadmap_idea TEXT NOT NULL DEFAULT '')")
        columns = {row[1] for row in db.execute('PRAGMA table_info(interest)')}
        for column in ('deposit_interest', 'vip_interest'):
            if column not in columns:
                db.execute(f'ALTER TABLE interest ADD COLUMN {column} INTEGER NOT NULL DEFAULT 0')
        if 'roadmap_idea' not in columns:
            db.execute("ALTER TABLE interest ADD COLUMN roadmap_idea TEXT NOT NULL DEFAULT ''")
        db.execute('CREATE TABLE IF NOT EXISTS custom_request (id INTEGER PRIMARY KEY, email TEXT NOT NULL, details TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, status TEXT NOT NULL DEFAULT \'new\')')


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Avoid recording submitted addresses, bodies, or query strings.
        print('%s %s' % (self.client_address[0], self.command), flush=True)

    def respond(self, code, body):
        data = body.encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == '/health':
            self.respond(200, '{"ok":true}')
        else:
            self.respond(404, '{"error":"Not found"}')

    def do_POST(self):
        if self.path not in ('/interest', '/custom'):
            return self.respond(404, '{"error":"Not found"}')
        if self.headers.get('Origin') != SITE_ORIGIN:
            return self.respond(403, '{"error":"Open the eGauge website to sign up."}')
        if self.headers.get_content_type() != 'application/x-www-form-urlencoded' and self.headers.get_content_type() != 'multipart/form-data':
            return self.respond(415, '{"error":"Unsupported form format."}')
        length = self.headers.get('Content-Length')
        if not length or not length.isdigit() or int(length) > 16384:
            return self.respond(413, '{"error":"Form is too large."}')
        with lock:
            now = time.monotonic()
            address = self.headers.get('X-Forwarded-For', self.client_address[0]).split(',')[0].strip()
            if now - last_attempt.get(address, 0) < 2:
                return self.respond(429, '{"error":"Please wait a moment and try again."}')
            last_attempt[address] = now
            if len(last_attempt) > 5000:
                last_attempt.clear()
        # Browser FormData uses multipart; parse with standard-library email parser.
        body = self.rfile.read(int(length))
        if self.headers.get_content_type() == 'multipart/form-data':
            from email.parser import BytesParser
            from email.policy import default
            message = BytesParser(policy=default).parsebytes(b'Content-Type: ' + self.headers['Content-Type'].encode() + b'\r\nMIME-Version: 1.0\r\n\r\n' + body)
            fields = {part.get_param('name', header='content-disposition'): part.get_content() for part in message.iter_parts()}
        else:
            fields = {key: values[0] for key, values in parse_qs(body.decode('utf-8'), keep_blank_values=True).items()}
        if fields.get('website'):
            return self.respond(200, '{"ok":true}')
        email = str(fields.get('email', '')).strip().lower()
        if len(email) > 254 or not EMAIL.fullmatch(email) or '..' in email:
            return self.respond(400, '{"error":"Enter a valid email address."}')
        try:
            with database() as db:
                if self.path == '/interest':
                    deposit = int(fields.get('deposit_interest') == 'yes')
                    vip = int(fields.get('vip_interest') == 'yes')
                    idea = str(fields.get('roadmap_idea', '')).strip()
                    if len(idea) > 500:
                        return self.respond(400, '{"error":"Please keep the roadmap idea under 500 characters."}')
                    db.execute('INSERT INTO interest(email, consent_text, deposit_interest, vip_interest, roadmap_idea) VALUES (?, ?, ?, ?, ?) ON CONFLICT(email) DO UPDATE SET deposit_interest = max(deposit_interest, excluded.deposit_interest), vip_interest = max(vip_interest, excluded.vip_interest), roadmap_idea = CASE WHEN excluded.roadmap_idea != \'\' THEN excluded.roadmap_idea ELSE interest.roadmap_idea END',
                               (email, 'Email me about eGauge preorder availability and selected options. No payment or reservation is taken.', deposit, vip, idea))
                else:
                    details = str(fields.get('details', '')).strip()
                    if len(details) < 20 or len(details) > 3000:
                        return self.respond(400, '{"error":"Please describe your project in 20 to 3000 characters."}')
                    db.execute('INSERT INTO custom_request(email, details) VALUES (?, ?)', (email, details))
        except sqlite3.Error:
            return self.respond(503, '{"error":"Unable to save your email right now. Please try again."}')
        self.respond(200, '{"ok":true}')


if __name__ == '__main__':
    initialize()
    ThreadingHTTPServer(('0.0.0.0', 8081), Handler).serve_forever()
