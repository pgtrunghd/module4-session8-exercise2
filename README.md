# module4-session8-exercise2 — Appointment-Service Fallback Method

Microservice quản lý lịch khám (port 8083), kết nối PostgreSQL `appointment_db` và đăng ký vào Eureka Server.

Khi tạo lịch khám, service gọi `patient-service` và `doctor-service` bằng `RestTemplate` + `@LoadBalanced` (theo tên service trên Eureka) để kiểm tra bệnh nhân và bác sĩ có tồn tại hay không.

Lời gọi tới `doctor-service` được bảo vệ bằng Resilience4j Circuit Breaker `doctorServiceCB`. Khi có ít nhất 5 lời gọi và tỷ lệ lỗi đạt 50%, mạch chuyển sang `OPEN`; các lời gọi tiếp theo trả HTTP 503 ngay mà không gửi request tới Doctor-Service. Sau 10 giây, mạch cho phép tối đa 3 lời gọi thử ở trạng thái `HALF_OPEN`. Bác sĩ không tồn tại (HTTP 404) không được tính là lỗi của service.

Khi Doctor-Service không khả dụng, `getDoctorFallback` trả về `ApiResponseError` với thông báo thân thiện. API trả HTTP 503 và JSON `{ "status": 503, "message": "Hiện tại không thể kiểm tra thông tin bác sĩ, vui lòng thử lại sau vài giây" }`.

- Chạy: `DB_PASSWORD=<mật_khẩu_PostgreSQL> ./gradlew bootRun` (cần DB `appointment_db`, Eureka Server, patient-service, doctor-service)
- API: `POST /api/v1/appointments` với body `{"patientId": 1, "doctorId": 1, "appointmentDate": "2026-11-01T09:00:00", "reason": "..."}`
  - 201: tạo thành công, status mặc định `PENDING`
  - 400: patientId/doctorId không tồn tại hoặc thiếu
  - 503: không gọi được service phụ thuộc

## Kiểm tra Circuit Breaker

1. Chạy PostgreSQL, Eureka, Patient-Service và Appointment-Service; tắt Doctor-Service.
2. Gửi cùng một request `POST /api/v1/appointments` hợp lệ 5 lần liên tiếp. Dùng `patientId` đang tồn tại để mỗi request đi tới bước gọi Doctor-Service. Mỗi lần trả HTTP 503.
3. Gửi lần thứ 6 ngay sau đó (trong vòng 10 giây). Request vẫn trả HTTP 503 nhưng không còn gọi Doctor-Service.
4. Xem sự kiện tại `http://localhost:8083/actuator/circuitbreakerevents`; tìm sự kiện `STATE_TRANSITION` của `doctorServiceCB` từ `CLOSED` sang `OPEN`. Danh sách mạch có tại `/actuator/circuitbreakers`.
5. Bật lại Doctor-Service, đợi 10 giây rồi gửi tiếp các request để quan sát `HALF_OPEN` và `CLOSED` khi service hoạt động bình thường.

Chạy kiểm thử tự động bằng `./gradlew test`. Test xác nhận mạch mở sau 5 lỗi, chặn lời gọi thứ 6 và bỏ qua lỗi bác sĩ không tồn tại.
