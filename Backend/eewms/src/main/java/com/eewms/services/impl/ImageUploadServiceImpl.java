package com.eewms.services.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.eewms.services.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImageUploadServiceImpl implements ImageUploadService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadImage(MultipartFile file) {
        try {
            String uniqueFileName = "avatar_" + System.currentTimeMillis();

            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "public_id", uniqueFileName,
                    "overwrite", true,
                    "invalidate", true,
                    "resource_type", "image"
            );


            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            throw new RuntimeException("Lỗi upload ảnh lên Cloudinary: " + e.getMessage(), e);
        }
    }

    // Xoá ảnh theo URL
    @Override
    public void deleteImageByUrl(String imageUrl) {
        try {
            String publicId = extractPublicId(imageUrl);
            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, Map.of("invalidate", true));
            }
        } catch (Exception e) {
            throw new RuntimeException("Không thể xoá ảnh trên Cloudinary: " + e.getMessage(), e);
        }
    }

    // Trích xuất public_id từ URL để dùng cho delete
    private String extractPublicId(String url) {
        try {
            String part = url.substring(url.indexOf("/upload/") + 8); // phần sau /upload/
            return part.substring(0, part.lastIndexOf('.')); // bỏ phần mở rộng
        } catch (Exception e) {
            return null;
        }
    }
}

