package com.eewms.repository;

import com.eewms.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username); // dùng cho đăng nhập

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN u.roles r
            WHERE 
                LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(u.address) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    // Projection lite cho view tìm kiếm & bảng nhân sự
    interface UserLiteView {
        Long getId();

        String getFullName();

        String getPhone();
    }

    /**
     * MANAGER candidates: mọi user có ROLE_MANAGER
     * JPQL (dựa trên quan hệ User.roles)
     */
    @Query("""
                select u.id as id, u.fullName as fullName, u.phone as phone
                from User u
                join u.roles r
                where r.name = 'ROLE_MANAGER'
                order by lower(u.fullName) asc
            """)
    List<UserLiteView> findAllManagersLite();

    /**
     * STAFF candidates: user có ROLE_STAFF và CHƯA thuộc kho nào.
     * Dùng native vì nhiều dự án map bảng trung gian warehouse_staff khác nhau.
     * Yêu cầu có các bảng: users, roles, user_roles, warehouse_staff(user_id, warehouse_id)
     */
    @Query("""
                select u.id as id, u.fullName as fullName, u.phone as phone
                from User u
                join u.roles r
                where r.name = 'ROLE_STAFF'
                  and not exists (
                      select 1 from WarehouseStaff ws where ws.user.id = u.id
                  )
                order by lower(u.fullName) asc
            """)
    List<UserLiteView> findUnassignedStaffLite();

    /**
     * Danh sách STAFF theo warehouse (để render bảng thành viên).
     * Dùng native để tránh phụ thuộc mapping ManyToMany cụ thể.
     */
    @Query("""
                select u.id as id, u.fullName as fullName, u.phone as phone
                from WarehouseStaff ws
                join ws.user u
                where ws.warehouse.id = :warehouseId
                order by lower(u.fullName) asc
            """)
    List<UserLiteView> findStaffByWarehouseLite(Integer warehouseId);
}
