from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import Application, CommandHandler, MessageHandler, ConversationHandler, filters, ContextTypes, CallbackQueryHandler
from config import TELEGRAM_TOKEN
from database import db
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Состояния разговора
CHOOSING_ROLE, CREATING_REQUEST, TITLE, DESCRIPTION, CATEGORY, VIEWING_REQUESTS, RESPONDING = range(7)

class PsychologyBot:
    def __init__(self):
        self.application = Application.builder().token(TELEGRAM_TOKEN).build()
        self.setup_handlers()
    
    def setup_handlers(self):
        """Установить обработчики команд"""
        
        # /start команда
        self.application.add_handler(CommandHandler("start", self.start))
        
        # Обработчик выбора роли
        self.application.add_handler(CallbackQueryHandler(self.role_selected, pattern="role_"))
        
        # Обработчик категорий
        self.application.add_handler(CallbackQueryHandler(self.category_selected, pattern="category_"))
        
        # Обработчик просмотра заявок
        self.application.add_handler(CallbackQueryHandler(self.view_request, pattern="request_"))
        
        # Обработчик текстовых сообщений
        conv_handler = ConversationHandler(
            entry_points=[CallbackQueryHandler(self.start_create_request, pattern="create_request")],
            states={
                TITLE: [MessageHandler(filters.TEXT & ~filters.COMMAND, self.get_title)],
                DESCRIPTION: [MessageHandler(filters.TEXT & ~filters.COMMAND, self.get_description)],
                CATEGORY: [CallbackQueryHandler(self.get_category, pattern="cat_")],
            },
            fallbacks=[CommandHandler("cancel", self.cancel)],
        )
        self.application.add_handler(conv_handler)
    
    async def start(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Команда /start"""
        user = db.get_or_create_user(update.effective_user.id)
        
        keyboard = [
            [InlineKeyboardButton("🆘 Мне нужна помощь", callback_data="role_requester")],
            [InlineKeyboardButton("💪 Помочь другому", callback_data="role_helper")],
        ]
        reply_markup = InlineKeyboardMarkup(keyboard)
        
        await update.message.reply_text(
            "Привет! 👋\n\nЭто анонимный бот психологической поддержки.\n\n"
            "Что ты хочешь сделать?",
            reply_markup=reply_markup
        )
    
    async def role_selected(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Обработка выбора роли"""
        query = update.callback_query
        await query.answer()
        
        role = query.data.split("_")[1]
        
        if role == "requester":
            keyboard = [[InlineKeyboardButton("Создать заявку", callback_data="create_request")]]
            reply_markup = InlineKeyboardMarkup(keyboard)
            await query.edit_message_text(
                "Ты выбрал роль 🆘 человека, нуждающегося в помощи.\n\n"
                "Расскажи нам о своей проблеме, и другие люди смогут тебе помочь!",
                reply_markup=reply_markup
            )
        else:
            await query.edit_message_text("Загружаю список заявок...")
            await self.show_requests(query, context)
    
    async def start_create_request(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Начало создания заявки"""
        query = update.callback_query
        await query.answer()
        
        await query.edit_message_text("Напиши название твоей проблемы (одна строка):")
        return TITLE
    
    async def get_title(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Получить название"""
        context.user_data['title'] = update.message.text
        await update.message.reply_text(
            "Спасибо! Теперь подробно опиши свою проблему:"
        )
        return DESCRIPTION
    
    async def get_description(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Получить описание"""
        context.user_data['description'] = update.message.text
        
        keyboard = [
            [InlineKeyboardButton("😢 Буллинг/Травля", callback_data="cat_bullying")],
            [InlineKeyboardButton("😔 Депрессия/Грусть", callback_data="cat_depression")],
            [InlineKeyboardButton("😰 Тревога/Стресс", callback_data="cat_anxiety")],
            [InlineKeyboardButton("💔 Отношения", callback_data="cat_relations")],
            [InlineKeyboardButton("📚 Учеба", callback_data="cat_study")],
            [InlineKeyboardButton("🏠 Семья", callback_data="cat_family")],
            [InlineKeyboardButton("❓ Другое", callback_data="cat_other")],
        ]
        reply_markup = InlineKeyboardMarkup(keyboard)
        
        await update.message.reply_text(
            "Выбери категорию проблемы:",
            reply_markup=reply_markup
        )
        return CATEGORY
    
    async def get_category(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Получить категорию"""
        query = update.callback_query
        await query.answer()
        
        category = query.data.split("_")[1]
        context.user_data['category'] = category
        
        user = db.get_or_create_user(update.effective_user.id)
        request_id = db.create_request(
            str(user['id']),
            context.user_data['title'],
            context.user_data['description'],
            category
        )
        
        await query.edit_message_text(
            f"✅ Твоя заявка создана!\n\n"
            f"ID: `{request_id}`\n\n"
            f"Люди будут видеть твою проблему и смогут помочь. "
            f"Никто не узнает, что это написал именно ты! 🤐"
        )
        return ConversationHandler.END
    
    async def show_requests(self, query, context: ContextTypes.DEFAULT_TYPE):
        """Показать список заявок"""
        requests = db.get_open_requests()
        
        if not requests:
            await query.edit_message_text("Пока нет открытых заявок 😢")
            return
        
        message = "📋 **Открытые заявки:**\n\n"
        keyboard = []
        
        for i, req in enumerate(requests[:10], 1):
            category_emoji = {
                'bullying': '😢',
                'depression': '😔',
                'anxiety': '😰',
                'relations': '💔',
                'study': '📚',
                'family': '🏠',
                'other': '❓'
            }.get(req['category'], '❓')
            
            message += f"{i}. {category_emoji} {req['title']}\n"
            keyboard.append([
                InlineKeyboardButton(
                    f"#{i}",
                    callback_data=f"request_{req['id']}"
                )
            ])
        
        reply_markup = InlineKeyboardMarkup(keyboard)
        await query.edit_message_text(message, reply_markup=reply_markup, parse_mode="Markdown")
    
    async def view_request(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Просмотр конкретной заявки"""
        query = update.callback_query
        await query.answer()
        
        request_id = query.data.split("_")[1]
        request = db.get_request_by_id(request_id)
        
        if not request:
            await query.edit_message_text("Заявка не найдена 😕")
            return
        
        responses = db.get_responses_for_request(request_id)
        
        message = f"""
📝 **Заявка**

**Проблема:** {request['title']}

**Описание:**
{request['description']}

---

💬 **Ответов:** {len(responses)}
"""
        
        if responses:
            message += "\n\n📨 **Последние ответы:**\n\n"
            for i, resp in enumerate(responses[:3], 1):
                message += f"{i}. {resp['message'][:100]}...\n"
        
        keyboard = [
            [InlineKeyboardButton("✍️ Ответить", callback_data=f"respond_{request_id}")],
            [InlineKeyboardButton("👍 Лайк", callback_data=f"like_{request_id}")],
            [InlineKeyboardButton("⬅️ Назад", callback_data="back")]
        ]
        reply_markup = InlineKeyboardMarkup(keyboard)
        
        await query.edit_message_text(message, reply_markup=reply_markup, parse_mode="Markdown")
    
    async def category_selected(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        await update.callback_query.answer()
        pass
    
    async def cancel(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Отмена"""
        await update.message.reply_text("Отменено ❌")
        return ConversationHandler.END
    
    def run(self):
        """Запустить бот"""
        self.application.run_polling()

if __name__ == "__main__":
    bot = PsychologyBot()
    db.init_db()
    bot.run()