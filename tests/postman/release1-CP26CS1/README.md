# Release 1 — Postman aligned with the supplied Excel

เทียบ Test Scripts ใน `Eggprompt_Release1_Test_Scripts.xlsx` ครบ **41 manual cases** จาก PBI-01 ถึง PBI-12 (ไม่ใช่ 40) โดยเก็บ Test IDs เดิมเป็น prefix เช่น `R1-11-01.01` คือ request แรกของเคส `R1-11-01`

ไฟล์ Excel ต้นฉบับไม่ถูกแก้ไข ชุด Postman เดิม 191 requests ยังคงอยู่ ชุดใหม่นี้แยกสำหรับขอบเขต Release 1 ตาม workbook

## มีอะไรบ้าง

- `EggPrompt-Release1-Aligned.postman_collection.json`: 98 requests รวม setup และคืนรหัสของบัญชีทดสอบเฉพาะ
- `EggPrompt-Release1-Email-Manual.postman_collection.json`: 10 requests สำหรับ reset password และ Add Farm มีจุดหยุดรับ token จาก inbox
- `EggPrompt-SIT.postman_environment.json`: environment สำหรับ VM ไม่ใส่รหัส Super Admin จริง
- `setup-release1-extra.sql`: fixture เพิ่มสำหรับ 11 ฟาร์มเพื่อทดสอบ pagination 10+1 และตัวกรอง active/suspended และบัญชีทดสอบเปลี่ยนรหัสโดยเฉพาะ
- `alignment.html`: ตารางเทียบ Excel Test ID / Expected เดิม / Postman Request IDs / ส่วนที่ต้องตรวจ Browser
- `alignment.json`: ข้อมูล traceability ชุดเดียวกัน

## เตรียมและรัน

1. ใช้ `setup-fixtures.sql` ชุดเดิมที่สร้าง PM-EXAM-A/B และบัญชี pm.* แล้ว ไม่ต้องรันซ้ำ
2. รัน `setup-release1-extra.sql` หนึ่งครั้งในฐานเดียวกับ VM ตามที่เลือกใช้ จะเพิ่มข้อมูลจริง ไม่ล้างหรือแก้ข้อมูลเดิม ถ้าเคยรันแล้วให้ใช้ต่อ
3. Import collection ใหม่และใช้ Environment EggPrompt SIT ที่ตั้งไว้แล้ว หรือ import environment จากชุดนี้แล้วใส่ superUsername/superPassword ของตน
4. รัน collection หลักทั้งชุดตามลำดับเดิม 1 iteration อย่าจัดลำดับตาม Test ID ใหม่ เพราะมี prerequisite เช่นต้องสร้างผู้ใช้ก่อนทดสอบ first-login และมี daily feed record ก่อนทดสอบห้ามย้ายวันที่รับฝูง
5. ตรวจ API assertions และใช้ `alignment.html` ช่วยกลับไปกรอกผลใน Excel
6. ต้องตรวจ Browser เพิ่มตาม Expected Results ก่อนให้สถานะ Pass แก่ manual case ทั้งข้อ

หาก account `pm.r1.password` เปลี่ยนรหัสสำเร็จแล้ว run หยุดกลางทาง รหัสปัจจุบันอาจเป็น `Changed@123` ให้ใช้ request `99 Restore password fixture` พร้อม token ของบัญชีนั้น หรือคืนรหัสของบัญชีทดสอบเฉพาะให้เป็น `Postman@123` ก่อน rerun ไม่ต้องแก้รหัส Super Admin/บัญชีจริง

## ข้อมูลตรงกับ workbook

- ฝูงรับเข้า D−7 จำนวน 100 ตัว โรงเรือนความจุ 100
- แก้ความจุโรงเรือน 100 → 120, boundary 0/1
- ฝูง 100 เท่าความจุผ่าน, 101 เกินความจุปฏิเสธ
- อาหาร 12.50 กก. ล็อต FEED-<RUN>, วันที่ Asia/Bangkok
- Jumbo 10 + เบอร์ 0 จำนวน 20 + เบอร์ 1 จำนวน 30 = ไข่ดี 60
- ไข่เสีย 3, รวม 63, แผงไข่ดี 2.00, สัดส่วนไข่เสีย 4.76%
- สุขภาพ 100 → ตาย 2 คัดทิ้ง 1 → 97, เพิ่มตายอีก 1 → 96
- ทดสอบ D−8 / D−7 / D+1 ด้วยวันที่สร้างจากวันรันใน Asia/Bangkok
- เก็บ SQL ข้อมูล 11 ฟาร์มแยกชื่อ R1 Postman เพื่อทดสอบ pagination 10+1 โดยไม่อาศัยจำนวนฟาร์มจริง

## สิ่งที่ Postman ตรวจแทน Browser ไม่ได้

การปิดบังรหัสผ่าน, ข้อความใต้ช่องฟอร์ม, เมนูถูกซ่อน, landing page ตาม role, Back/refresh, การคงบริบทฟาร์มใน browser storage, modal และลักษณะหน้าจอ

R1-03-04 ตรวจ API ตอบ 403 ได้ แต่ยังต้องยืนยันว่าเมนูไม่แสดงจริงใน Browser
R1-06-02 ส่ง scope ซ้ำแล้วอ่านข้อมูลฟาร์มเดิมได้ แต่ไม่พิสูจน์ว่า Browser จำ farm context หลัง refresh

API pass อย่างเดียวจึงไม่เท่ากับ manual case pass ทั้งข้อ ไม่ย้าย 518 assertions จากชุดเก่ามาใส่ชุดใหม่นี้โดยไม่รัน

## อีเมลและ Add Farm

Collection อีเมลแยกจากชุดหลัก เพราะต้องใช้ inbox QA จริง ตั้ง `runEmailTests=true`, `resetUsername`, `resetCurrentPassword` เป็นบัญชี QA Farm Admin ที่ active และยืนยันอีเมลแล้ว พร้อมกำหนด `addFarmEmail` เป็น inbox QA อีกบัญชี

- รัน Mail prerequisites เพื่อ login Super Admin
- R1-02-03.01 login บัญชี QA แยก
- R1-02-03.02 ขอ reset; หยุดเปิดอีเมลแล้วคัด query parameter `token` ใส่ `resetToken`
- R1-02-03.03 ตั้งรหัสใหม่ / .04 login ด้วยรหัสใหม่
- R1-02-04 ปฏิเสธ token ที่ใช้แล้ว และตรวจว่ารหัสล่าสุดยังใช้ได้
- R1-05-01 สร้างฟาร์มพร้อม Farm Admin, ค้นหาฟาร์ม และยืนยันว่าบัญชีผูกกับฟาร์มใหม่

HTTP สำเร็จไม่ใช่หลักฐานว่า SMTP ส่งถึง inbox ต้องตรวจอีเมลจริง ใช้บัญชี QA แยกเพราะ reset เปลี่ยนรหัสจริง

## Header X-Farm-Id (แก้ปัญหา farm scope ผ่าน proxy)

FE, BE และ Collection ใช้ header `X-Farm-Id` แทน `farm_id` เพื่อหลีกเลี่ยง proxy ที่ทิ้ง header ชื่อมี underscore การตรวจสิทธิ์และ expected farm scope คงเดิม

ต้องนำ Backend และ Frontend เวอร์ชันนี้ขึ้น VM แล้วจึง Import `EggPrompt-Release1-Ready-To-Run.postman_collection.json` ที่แก้ใหม่ เลือก Environment `EggPrompt SIT` เดิม และรันทั้ง Collection ตามลำดับเดิม ไม่ต้องรัน SQL fixture ซ้ำ ไม่ต้องทดสอบ Email หากข้ามส่วนนั้น

ตรวจ 4 requests ในโฟลเดอร์ 07: A → B → A → A ต้องได้ข้อมูลเฉพาะ farm ที่เลือก การส่ง header ว่าง/ซ้ำ/หลายค่า/ยาวเกิน 20 ยังต้องถูกปฏิเสธ และผู้ใช้ทั่วไปยังเลือก farm อื่นไม่ได้

ยังต้องยืนยันผลผ่าน Public URL หลัง deploy; การทดสอบ local ไม่ยืนยัน proxy ของมหาวิทยาลัย

## ผลและข้อมูลคงเหลือ

ชุดใหม่ตรวจ syntax/ตัวแปร/โครงสร้าง JSON แบบ offline แล้ว ยังไม่ได้รันกับ API จึงเป็น NOT RUN ทั้งหมด

เก็บข้อมูลที่สร้างไว้ให้ตรวจใน Browser และเก็บหลักฐาน ไม่รวมการล้างทั้งฐานหรือ cleanup หลังจบเคส เพราะจะทำให้ตรวจ UI ตาม Excel ต่อไม่ได้ `99 Restore password fixture` คืนเฉพาะรหัสของบัญชี dedicated เพื่อให้ rerun ได้


## แก้ไขลำดับ Restore

เวอร์ชันแก้ไขวาง `99 Restore password fixture` ไว้ท้ายสุด หลัง PBI-12 เนื่องจากเวอร์ชันก่อนเรียงตัวอักษรทำให้ 99 อยู่ก่อน PBI และคืนรหัสก่อนเปลี่ยนรหัส จึงเกิด 400 รหัสผ่านเดิมไม่ถูกต้อง

ถ้ารันเวอร์ชันก่อนจบแล้วและ PBI-02 เปลี่ยนรหัสผ่านสำเร็จ ให้เปิด request Restore ใน Collection เดิมแล้วกด Send หนึ่งครั้ง (ใช้ passwordToken ที่ยังเก็บอยู่) ควรได้ 200 เพื่อคืน pm.r1.password เป็น Postman@123 ก่อนเริ่ม run ใหม่

ผล Fail ของ farm scope ยังคงเดิม ไม่เปลี่ยน expected และไม่ถือว่าแก้ปัญหา proxy แล้ว
