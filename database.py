import psycopg2
from psycopg2.extras import RealDictCursor
from config import DATABASE_URL
import uuid
from datetime import datetime

class Database:
    def __init__(self):
        self.db_url = DATABASE_URL
    
    def get_connection(self):
        return psycopg2.connect(self.db_url)
    
    def init_db(self):
        """Создание таблиц"""
        conn = self.get_connection()
        cur = conn.cursor()
        
        cur.execute('''
            CREATE TABLE IF NOT EXISTS users (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                telegram_user_id BIGINT UNIQUE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ''')
        
        cur.execute('''
            CREATE TABLE IF NOT EXISTS requests (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                title VARCHAR(255) NOT NULL,
                description TEXT NOT NULL,
                category VARCHAR(50),
                status VARCHAR(20) DEFAULT 'open',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ''')
        
        cur.execute('''
            CREATE TABLE IF NOT EXISTS responses (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                request_id UUID NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
                user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                message TEXT NOT NULL,
                rating INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ''')
        
        cur.execute('''
            CREATE TABLE IF NOT EXISTS contacts (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                to_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                username VARCHAR(255),
                status VARCHAR(20) DEFAULT 'pending',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ''')
        
        conn.commit()
        cur.close()
        conn.close()
    
    def get_or_create_user(self, telegram_user_id):
        """Получить или создать пользователя"""
        conn = self.get_connection()
        cur = conn.cursor(cursor_factory=RealDictCursor)
        
        cur.execute('SELECT * FROM users WHERE telegram_user_id = %s', (telegram_user_id,))
        user = cur.fetchone()
        
        if not user:
            user_id = uuid.uuid4()
            cur.execute(
                'INSERT INTO users (id, telegram_user_id) VALUES (%s, %s)',
                (user_id, telegram_user_id)
            )
            conn.commit()
            user = {'id': user_id, 'telegram_user_id': telegram_user_id}
        
        cur.close()
        conn.close()
        return user
    
    def create_request(self, user_id, title, description, category):
        """Создать новую заявку"""
        conn = self.get_connection()
        cur = conn.cursor(cursor_factory=RealDictCursor)
        
        request_id = uuid.uuid4()
        cur.execute(
            '''INSERT INTO requests (id, user_id, title, description, category)
               VALUES (%s, %s, %s, %s, %s)''',
            (request_id, user_id, title, description, category)
        )
        conn.commit()
        cur.close()
        conn.close()
        return request_id
    
    def get_open_requests(self, limit=10):
        """Получить открытые заявки"""
        conn = self.get_connection()
        cur = conn.cursor(cursor_factory=RealDictCursor)
        
        cur.execute(
            '''SELECT id, title, description, category, created_at 
               FROM requests WHERE status = 'open' 
               ORDER BY created_at DESC LIMIT %s''',
            (limit,)
        )
        requests = cur.fetchall()
        cur.close()
        conn.close()
        return requests
    
    def get_request_by_id(self, request_id):
        """Получить заявку по ID"""
        conn = self.get_connection()
        cur = conn.cursor(cursor_factory=RealDictCursor)
        
        cur.execute('SELECT * FROM requests WHERE id = %s', (request_id,))
        request = cur.fetchone()
        cur.close()
        conn.close()
        return request
    
    def add_response(self, request_id, user_id, message):
        """Добавить ответ на заявку"""
        conn = self.get_connection()
        cur = conn.cursor(cursor_factory=RealDictCursor)
        
        response_id = uuid.uuid4()
        cur.execute(
            '''INSERT INTO responses (id, request_id, user_id, message)
               VALUES (%s, %s, %s, %s)''',
            (response_id, request_id, user_id, message)
        )
        conn.commit()
        cur.close()
        conn.close()
        return response_id
    
    def get_responses_for_request(self, request_id):
        """Получить ответы на заявку"""
        conn = self.get_connection()
        cur = conn.cursor(cursor_factory=RealDictCursor)
        
        cur.execute(
            '''SELECT id, message, rating, created_at 
               FROM responses WHERE request_id = %s 
               ORDER BY created_at DESC''',
            (request_id,)
        )
        responses = cur.fetchall()
        cur.close()
        conn.close()
        return responses

db = Database()