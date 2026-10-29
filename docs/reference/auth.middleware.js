// ============================================================
// C2-50: Middleware ตรวจสอบ JWT Token และ Role
//
// ติดตั้ง:  npm i jsonwebtoken
// .env:     JWT_SECRET=ตั้งค่าเป็นสตริงยาวๆ สุ่ม
//
// ตอน login ให้ sign token แบบนี้ (ใส่แค่ user_id พอ ส่วน role ดึงสดจาก DB):
//   jwt.sign({ user_id: user.user_id }, process.env.JWT_SECRET, { expiresIn: '8h' })
// ============================================================

const jwt = require('jsonwebtoken');
const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient();

// ตรวจ token แล้วใส่ req.user = { user_id, username, user_role, farm_id }
// และ req.farmId = ฟาร์มที่กำลังทำงานด้วย
//   - Farm Admin / Staff / Finance → farm_id ของตัวเอง
//   - Super Admin → ค่าจาก header X-Farm-Id ตอน "ตรวจสอบฟาร์ม" (ไม่มีก็เป็น null)
async function authenticate(req, res, next) {
    try {
        const [scheme, token] = (req.headers.authorization || '').split(' ');
        if (scheme !== 'Bearer' || !token) {
            return res.status(401).json({ message: 'Missing or invalid Authorization header' });
        }

        let payload;
        try {
            payload = jwt.verify(token, process.env.JWT_SECRET);
        } catch (err) {
            const message = err.name === 'TokenExpiredError' ? 'Token expired' : 'Invalid token';
            return res.status(401).json({ message });
        }

        // ดึงจาก DB ทุกครั้ง เพื่อให้การเปลี่ยน role / ปิดบัญชี / ลบ user มีผลทันที
        const user = await prisma.users.findFirst({
            where: { user_id: payload.user_id, deleted_at: null },
            select: {
                user_id: true,
                username: true,
                user_role: true,
                farm_id: true,
                is_active: true,
            },
        });

        if (!user || !user.is_active) {
            return res.status(401).json({ message: 'Account not found or disabled' });
        }

        req.user = user;
        req.farmId =
            user.user_role === 'Super Admin'
                ? req.headers['x-farm-id'] || null
                : user.farm_id;

        next();
    } catch (err) {
        console.error('authenticate error:', err);
        return res.status(500).json({ message: 'Internal server error' });
    }
}

// ใช้หลัง authenticate:  router.get('/x', authenticate, requireRole('Super Admin', 'Farm Admin'), handler)
function requireRole(...allowedRoles) {
    return (req, res, next) => {
        if (!req.user) {
            return res.status(401).json({ message: 'Unauthorized' });
        }
        if (!allowedRoles.includes(req.user.user_role)) {
            return res.status(403).json({ message: 'Forbidden' });
        }
        next();
    };
}

module.exports = { authenticate, requireRole };