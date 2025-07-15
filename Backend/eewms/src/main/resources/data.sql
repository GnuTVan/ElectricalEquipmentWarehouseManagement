INSERT INTO users (username, password, full_name, email, mobile, role_id, enabled)
VALUES (
           'namph',
           '123456',  -- BCrypt-hash
           'Phạm Hoàng Nam',
           'phamhoangnam@gmail.com',
           '0123456789',
           1,
           true
       );