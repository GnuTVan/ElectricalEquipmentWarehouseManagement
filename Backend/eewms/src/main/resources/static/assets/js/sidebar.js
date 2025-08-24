document.addEventListener("DOMContentLoaded", function () {
    const toggleBtn = document.getElementById("toggleSidebarBtn");
    const sidebar = document.getElementById("sidebar");
    const main = document.getElementById("mainContent");

    toggleBtn?.addEventListener("click", () => {
        const isHidden = sidebar.classList.toggle("-translate-x-full");
        main.classList.toggle("ml-0", isHidden);
        main.classList.toggle("ml-64", !isHidden);
    });
});

// <!-- JS toggle submenu trong sidebar -->

// <!--    sửa lại toggle submenu để nhận id truyền vào thay vì fix cứng(thêm data-submenu-id vào để gọi)-->
function toggleSubmenu(buttonEl) {
    const submenuId = buttonEl.getAttribute("data-submenu-id");
    const submenu = document.getElementById(submenuId);
    const arrow = buttonEl.querySelector('i.ri-arrow-down-s-line');
    const isOpen = submenu.classList.contains("open");

    if (isOpen) {
        submenu.style.maxHeight = "0px";
        submenu.classList.remove("open");
        if (arrow) arrow.style.transform = "rotate(0deg)";
        localStorage.setItem(submenuId + "-open", "false");
    } else {
        submenu.style.maxHeight = submenu.scrollHeight + "px";
        submenu.classList.add("open");
        if (arrow) arrow.style.transform = "rotate(-180deg)";
        localStorage.setItem(submenuId + "-open", "true");
    }
}

//  Khi load trang, kiểm tra từng submenu:
//  Nếu user trước đó mở thì giữ nguyên (dựa vào localStorage)
//  Hoặc nếu đang ở trang con liên quan thì mở dropdown tương ứng
document.addEventListener("DOMContentLoaded", function () {
    // URL hiện tại
    const currentPath = window.location.pathname;

    // Tự động xử lý tất cả các button có data-submenu-id
    document.querySelectorAll('[data-submenu-id]').forEach(buttonEl => {
        const submenuId = buttonEl.getAttribute("data-submenu-id");
        const submenu = document.getElementById(submenuId);
        const arrow = buttonEl.querySelector('i.ri-arrow-down-s-line');

        // Kiểm tra nếu localStorage lưu trạng thái mở
        const isSavedOpen = localStorage.getItem(submenuId + "-open") === "true";

        // Hoặc tự động mở nếu URL khớp pattern
        const shouldForceOpen = (
            (submenuId === "partner-submenu" && (currentPath.includes("/admin/suppliers") || currentPath.includes("/customers"))) ||
            (submenuId === "setting-submenu" && currentPath.includes("/settings"))
        );

        if (submenu && (isSavedOpen || shouldForceOpen)) {
            submenu.classList.add("open");
            submenu.style.transition = "none";
            submenu.style.maxHeight = submenu.scrollHeight + "px";

            if (arrow) {
                arrow.style.transition = "none";
                arrow.style.transform = "rotate(-180deg)";
            }

            requestAnimationFrame(() => {
                submenu.style.transition = "";
                if (arrow) arrow.style.transition = "";
            });
        }
    });
});