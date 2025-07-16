package com.eewms.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "good_issue_note")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodIssueNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ginId;

    @Column(name = "gin_code", length = 50, unique = true, nullable = false)
    @NotBlank(message = "GIN code cannot be blank")
    private String ginCode;


    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime issueDate;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne
    @JoinColumn(name = "customer", nullable = true)
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    @NotNull(message = "Created by is required")
    private User createdBy;

    @OneToMany(mappedBy = "goodIssueNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodIssueDetail> details;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = true)
    private String status ;
//    private GinStatus status = GinStatus.PENDING;

//    public enum GinStatus {
//        PENDING("Chờ nhận"),
//        IN_PROGRESS("Đã nhập một phần"),
//        COMPLETED("Hoàn thành"),
//        CANCELED("Hủy");
//
//        private final String label;
//
//        GinStatus(String label) {
//            this.label = label;
//        }
//
//        public String getLabel() {
//            return label;
//        }
//    }

}

