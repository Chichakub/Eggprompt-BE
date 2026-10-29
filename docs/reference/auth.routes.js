// ============================================================
// C2-54  GET  /api/v1/auth/me      ดึงข้อมูลโปรไฟล์ผู้ใช้ที่ล็อกอินอยู่
// C2-53  POST /api/v1/auth/logout  ออกจากระบบ
//
// Mount ใน app.js:
//   const { authenticate } = require('./auth.middleware');
//   app.use('/api/v1/auth', require('./auth.routes'));
//   app.use('/api/v1/users', authenticate, require('./users.routes'));
//
// (POST /auth/login อยู่ที่ ticket อื่น ต้อง sign JWT ตามคอมเมนต์ใน auth.middleware.js)
// ============================================================

const express = require('express');
const { PrismaClient } = require('@prisma/client');
const { authenticate } = require('./auth.middleware');

const prisma = new PrismaClient();
const router = express.Router();

router.get('/me', authenticate, async (req, res) => {
    try {
        const user = await prisma.users.findFirst({
            where: { user_id: req.user.user_id, deleted_at: null },
            select: {
                user_id: true,
                username: true,
                user_role: true,
                is_active: true,
                farm_id: true,
                created_at: true,
                // Super Admin ไม่มีฟาร์ม → farms จะเป็น null
                farms: {
                    select: { farm_id: true, farm_name: true, subscription_status: true },
                },
            },
        });

        if (!user) {
            return res.status(404).json({ message: 'User not found' });
        }
        return res.status(200).json({ data: user });
    } catch (err) {
        console.error('getMe error:', err);
        return res.status(500).json({ message: 'Internal server error' });
    }
});

// JWT เป็น stateless ฝั่ง server ไม่ต้องทำอะไรมาก
// client เป็นคนลบ token (ดู authStore.logout)
// ถ้าอนาคตอยากเพิกถอน token จริงๆ ค่อยเพิ่ม blacklist ตรงนี้
router.post('/logout', authenticate, (req, res) => {
    return res.status(200).json({ message: 'Logged out' });
});

module.exports = router;