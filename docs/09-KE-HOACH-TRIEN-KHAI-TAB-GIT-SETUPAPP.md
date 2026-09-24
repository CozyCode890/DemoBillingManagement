# Kế Hoạch Chi Tiết: Nâng Cấp GUI SetupApp - Triển Khai Tab Quản Lý Phiên Bản Git (Git Workflow)

> **Dành cho AI / Lập trình viên tiếp theo:** Tài liệu này chứa đặc tả kỹ thuật toàn diện, kiến trúc mã nguồn Java Swing, phân chia giai đoạn luồng làm việc (workflow stages) và logic tích hợp lệnh Git CLI cho Tab 4 trong ứng dụng `SetupApp.jar`. Hãy đọc kỹ và tuân thủ đúng các nguyên tắc bên dưới để triển khai hoàn thiện.

---

## 1. Bối Cảnh Dự Án & Quy Trình Git Của Nhóm

### 1.1. Đặc thù dự án & mô hình nhóm
- **Quy mô:** Nhóm gồm 4 thành viên đang học tập và phát triển dự án bán hàng `DemoBillingManagement`:
  - **2 dev SQL:** Làm việc độc lập tại thư mục `sql/` (viết DDL schema, seed data, stored procedures và functions).
  - **2 dev Java:** Làm việc độc lập tại thư mục `src/billing/` (xây dựng Models, DAOs, Swing UI, kết nối MySQL qua JDBC và gọi các functions từ MySQL server).
- **Mục tiêu nâng cấp:** Bạn là người hướng dẫn, muốn xây dựng một giao diện Git trực quan ngay trong ứng dụng quản lý môi trường (`SetupApp.jar`) để:
  1. Hướng dẫn nhóm làm quen với quy trình Git chuẩn, hạn chế nhầm lẫn nhánh hoặc thao tác sai.
  2. Dưới mỗi nút bấm luôn hiển thị câu lệnh Git CLI tương đương để các bạn **học và ghi nhớ cú pháp dòng lệnh**.
  3. Kết quả chạy được stream trực tiếp vào bảng đen console log để tiện theo dõi.

### 1.2. Mô hình phân nhánh chuẩn (Git Branching Model)
```
               [origin/main] (Nhánh chính - Production / Final)
                     ▲
                     │ (Pull Request khi dev đã chạy ổn định)
               [origin/dev]  (Nhánh tích hợp chung của toàn bộ nhóm)
                ▲         ▲
 (PR merge)     │         │     (PR merge)
   ┌────────────┴──┐   ┌──┴─────────────┐
   │               │   │                │
[origin/java/..]   │   │   [origin/sql/..] (Nhánh remote cá nhân)
   ▲               │   │                ▲
   │ (git push -u) │   │ (git push -u)  │
[local: java/..] ──┘   └── [local: sql/..] (Nhánh con đang code)
```

- **Quy ước đặt tên nhánh con:**
  - Nhóm Java: `java/model/taskxxx`, `java/ui/taskyyy`,...
  - Nhóm SQL: `sql/table/taskxxx`, `sql/routine/taskyyy`,...
- **Luồng làm việc hoàn hảo hằng ngày (Daily Golden Flow):**
  1. **Bước 1:** Bắt đầu ngày mới, lấy toàn bộ commit mới nhất từ `origin` về máy (`git fetch`).
  2. **Bước 2:** Chuyển sang nhánh con của mình (`git switch <branch>`) hoặc cập nhật nhánh `dev` (`git pull --ff-only`).
  3. **Bước 3:** Tích hợp các cập nhật mới nhất từ `dev` vào nhánh con (`git merge origin/dev` hoặc `git merge dev`) để giải quyết xung đột sớm nếu có.
  4. **Bước 4:** Lập trình, kiểm thử và commit mã nguồn tại nhánh con.
  5. **Bước 5:** Đẩy commit lên nhánh con trên remote (`git push -u origin <branch>`).
  6. **Bước 6:** Mở Pull Request trên GitHub/GitLab gộp nhánh con vào `dev`. Trưởng nhóm review và merge.

---

## 2. Thiết Kế Giao Diện Tab Git (Tab 4)

### 2.1. Kiến trúc bố cục tổng thể (Scrollable + Non-linear Grouping)
- **Vấn đề cần giải quyết:** Tab Git có nhiều nhóm tính năng, nếu hiển thị tuyến tính một hàng dài sẽ bị quá khổ, khó dùng và không phản ánh đúng quy trình.
- **Giải pháp bố cục:**
  - **Phần trên (Controls Panel):** Đặt trong `JScrollPane` (cuộn dọc tự động khi kích thước cửa sổ thu nhỏ, không cuộn ngang). Chia thành **4 Thẻ Giai đoạn (Stage Cards)** viền mảnh, nền trắng theo đúng thứ tự công việc.
  - **Phần dưới (Console Log):** Bảng đen `LogConsole` (màu nền `#181A1E`, chữ `#C8DCF0`) stream output thời gian thực, có nút Sao chép, Xoá log, Huỷ tiến trình.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ TAB 4: QUẢN LÝ PHIÊN BẢN GIT (GIT WORKFLOW)                                 │
│ [🌿 Nhánh hiện tại: java/model/task101 | 📌 Commit: 8a2b3c | [🔄 Làm mới]]  │
├─────────────────────────────────────────────────────────────────────────────┤
│ ┌─ GIAI ĐOẠN 1: KIỂM TRA TRẠNG THÁI (INSPECT & STATUS) ───────────────────┐ │
│ │ [ git status ]                                                          │ │
│ │ 💻 Lệnh CLI: $ git status                                                │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ ┌─ GIAI ĐOẠN 2: LẤY CẬP NHẬT TỪ XA & CHUYỂN NHÁNH (SYNC & SWITCH) ────────┐ │
│ │ Hàng 2A (Fetch):                                                        │ │
│ │   [ git fetch ]  [ ] All branches  Nhánh từ xa: [ origin/dev         ▼] │ │
│ │   💻 Lệnh CLI: $ git fetch origin dev                                    │ │
│ │                                                                         │ │
│ │ Hàng 2B (Switch):                                                       │ │
│ │   [ git switch ] Nhánh muốn chuyển: [ dev                            ▼] │ │
│ │   💻 Lệnh CLI: $ git switch dev                                          │ │
│ │                                                                         │ │
│ │ Hàng 2C (Pull):                                                         │ │
│ │   [ git pull ]   [x] ff only (Kéo an toàn, không tạo commit rác)         │ │
│ │   💻 Lệnh CLI: $ git pull --ff-only                                      │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ ┌─ GIAI ĐOẠN 3: TÍCH HỢP CODE VÀO NHÁNH LÀM VIỆC (MERGE INTEGRATION) ────┐ │
│ │ [Nguồn: origin ▼] [origin/dev ▼]  ➔ [GỘP VÀO] ➔  [Đích: java/task101 ▼] │ │
│ │ [ git merge ]                                                           │ │
│ │ 💻 Lệnh CLI: $ git merge origin/dev                                      │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ ┌─ GIAI ĐOẠN 4: ĐẨY CODE & CHUẨN BỊ PULL REQUEST (SHIP & PR) ────────────┐ │
│ │ Nhánh sẽ đẩy: [ java/model/task101 (nhánh hiện tại) ]                   │ │
│ │ Tuỳ chọn: [x] -u origin <branch>   [ ] --force-with-lease               │ │
│ │ [ git push ]                                                            │ │
│ │ 💻 Lệnh CLI: $ git push -u origin java/model/task101                     │ │
│ │ 💡 Gợi ý: Sau khi push, hãy mở GitHub/GitLab để tạo PR gộp vào 'dev'!    │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ ┌─ BẢNG ĐEN TERMINAL (CONSOLE LOG REAL-TIME) ─────────────────────────────┐ │
│ │ > [THỰC THI] git fetch origin dev                                       │ │
│ │ From https://github.com/CozyCode890/DemoBillingManagement               │ │
│ │  * branch            dev        -> FETCH_HEAD                           │ │
│ │ [XONG] Lệnh kết thúc lúc 20:55:10 (mã thoát 0).                         │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Đặc Tả Chi Tiết 4 Giai Đoạn & Thành Phần Điều Khiển

### 3.1. Thanh Trạng Thái Đầu Trang (Top Info Bar)
- **Vị trí:** Nằm ngay đầu tab Git, cố định trên khung cuộn.
- **Thành phần:**
  - **Badge nhánh hiện tại:** Nền xanh nhẹ (`#E8F4FD`), viền xanh, chữ đậm: `🌿 Nhánh hiện tại: <current_branch>`.
  - **Commit mới nhất:** Hiển thị 7 ký tự SHA và tóm tắt commit gần nhất: `📌 [a1b2c3d] feat: add invoice calculation`.
  - **Nút `[🔄 Làm mới]`:** Quét lại toàn bộ nhánh local/remote và làm mới dữ liệu cho toàn bộ các dropdown menu.

---

### 3.2. Giai Đoạn 1: Kiểm Tra Trạng Thái (Inspect & Status)
- **Mục đích:** Giúp lập trình viên kiểm tra mình đang ở nhánh nào, có file nào sửa đổi chưa commit, file untracked hay staged.
- **Thành phần giao diện:**
  - Nút: `[ git status ]` (Màu xanh dương `UiHelper.BTN_BLUE`).
  - Dòng lệnh học tập:
    ```
    💻 Lệnh CLI: $ git status
    ```
- **Hành vi:**
  - Chạy `git status` trong thư mục gốc. Output xuất ra bảng đen.

---

### 3.3. Giai Đoạn 2: Đồng Bộ Từ Xa & Điều Hướng Nhánh (Sync & Switch)

#### Hàng 2A: `git fetch` (Lấy dữ liệu mới nhất từ remote)
- **Nút:** `[ git fetch ]` (Màu `UiHelper.BTN_BLUE`).
- **Checkbox:** `[ ] All branches` (nhãn: "Tất cả các nhánh (--all)"). Mặc định: **Bỏ tick**.
- **Dropdown Menu (JComboBox):**
  - Danh sách toàn bộ các nhánh remote lấy từ `git branch -r`.
  - Quy cách hiển thị: **Luôn có tiền tố `"origin/"` ở đầu** (ví dụ: `origin/dev`, `origin/main`, `origin/java/model/taskxxx`).
  - **Giá trị mặc định:** Tự động chọn nhánh `origin/<nhánh_local_hiện_tại>`. Nếu remote chưa có nhánh tương ứng thì fallback về `origin/dev`, nếu không có `dev` thì `origin/main`.
  - **Ràng buộc tương tác:** Khi tick vào "All branches", dropdown nhánh tự động bị `setEnabled(false)` (mờ đi).
- **Lệnh thực thi:**
  - Khi tick "All branches": `git fetch --all --prune`
  - Khi không tick: `git fetch origin <tên_nhánh_đã_bỏ_origin/>`
- **Dòng lệnh học tập:** Cập nhật preview động ngay khi thay đổi lựa chọn.

#### Hàng 2B: `git switch` (Chuyển nhánh làm việc)
- **Nút:** `[ git switch ]` (Màu `UiHelper.BTN_GREEN`).
- **Dropdown Menu (JComboBox):**
  - Liệt kê toàn bộ nhánh local hiện có.
  - **Giá trị mặc định:** Nhánh `dev`. Nếu máy chưa có nhánh `dev`, chọn `main`.
- **Lệnh thực thi:** `git switch <tên_nhánh_chọn>`
- **Dòng lệnh học tập:** `💻 Lệnh CLI: $ git switch <branch>`
- **Sự kiện sau khi chạy:** Tự động cập nhật lại badge nhánh hiện tại và làm mới các dropdown.

#### Hàng 2C: `git pull` (Kéo commit mới về nhánh hiện tại)
- **Nút:** `[ git pull ]` (Màu `UiHelper.BTN_GREEN`).
- **Checkbox:** `[x] ff only` (nhãn: "Chỉ fast-forward (--ff-only)").
  - **Mặc định: Tick chọn**.
  - **Mục đích giáo dục:** Rất quan trọng khi hướng dẫn người mới. Tùy chọn `--ff-only` đảm bảo git chỉ cập nhật nếu nhánh local đi sau nhánh remote thẳng hàng; nếu hai nhánh đã rẽ nhánh (diverged), lệnh sẽ dừng lại và cảnh báo thay vì tự động tạo commit merge rác (`Merge branch 'dev' of ...`).
- **Lệnh thực thi:**
  - Nếu tick: `git pull --ff-only`
  - Nếu bỏ tick: `git pull`
- **Dòng lệnh học tập:** `💻 Lệnh CLI: $ git pull --ff-only`

---

### 3.4. Giai Đoạn 3: Tích Hợp Mã Nguồn Vào Nhánh Làm Việc (Merge Integration)
Đây là tính năng quan trọng nhất cho quy trình hằng ngày: Kéo mã nguồn mới nhất trên `dev` vào nhánh con đang code để tránh xung đột khi tạo PR.

- **Cấu trúc 3 Menu liên kết:**
  - **Menu 1 (Loại nguồn):** Dropdown gồm 2 tuỳ chọn: `origin` hoặc `local`.
  - **Menu 2 (Nhánh nguồn):** Danh sách nhánh dựa theo Menu 1:
    - Khi Menu 1 chọn `origin`: Liệt kê các nhánh remote có tiền tố `origin/` (ví dụ: `origin/dev`, `origin/main`).
    - Khi Menu 1 chọn `local`: Liệt kê các nhánh local (ví dụ: `dev`, `main`).
  - **Ký hiệu đánh dấu:** Đặt giữa Menu 2 và Menu 3:
    - Nhãn ký hiệu đồ hoạ: `➔ [GỘP VÀO] ➔` (nổi bật, rõ hướng di chuyển của code).
  - **Menu 3 (Nhánh đích):** Danh sách các nhánh local.
- **Giá trị mặc định chuẩn quy trình:**
  - Menu 1: `origin`
  - Menu 2: `origin/dev`
  - Ký hiệu: `➔ [GỘP VÀO] ➔`
  - Menu 3: Nhánh local hiện tại mà người dùng đang đứng (ví dụ `java/model/taskxxx`).
- **Nút bấm:** `[ git merge ]` (Màu tím `UiHelper.BTN_PURPLE`).
- **Lệnh thực thi:**
  - Nếu nhánh ở Menu 3 là nhánh hiện tại: Thực hiện `git merge <nhánh_nguồn>`
  - Nếu nhánh ở Menu 3 khác nhánh hiện tại: Tự động switch sang nhánh đích rồi merge: `git switch <nhánh_đích>` -> `git merge <nhánh_nguồn>`.
- **Dòng lệnh học tập:** `💻 Lệnh CLI: $ git merge origin/dev`

---

### 3.5. Giai Đoạn 4: Đẩy Code & Chuẩn Bị Pull Request (Ship & PR)
- **Mục đích:** Đẩy commit từ nhánh con cá nhân lên remote và nhắc nhở tạo PR vào `dev`.
- **Thành phần giao diện:**
  - Nhãn hiển thị nhánh sẽ đẩy: `Nhánh đẩy: 🌿 <current_branch>` (Mặc định luôn là nhánh hiện tại).
  - **Bộ Options (Tuỳ chọn cho push):**
    1. **`[x] Thiết lập upstream (-u origin <branch>)` (Mặc định BẬT khi nhánh chưa có upstream):**
       - Khắc phục triệt để lỗi người mới tạo nhánh local rồi gõ `git push` bị lỗi *"fatal: The current branch has no upstream branch"*.
    2. **`[ ] Ép an toàn (--force-with-lease)` (Mặc định TẮT):**
       - Tùy chọn an toàn khi rebase/amend commit cá nhân trước khi nộp PR, có tooltip cảnh báo không dùng trên nhánh chung (`dev`/`main`).
    3. **`[ ] Kèm tags (--tags)` (Mặc định TẮT).**
  - Nút: `[ git push ]` (Màu tím `UiHelper.BTN_PURPLE`).
- **Lệnh thực thi:**
  - Khi tick `-u`: `git push -u origin <current_branch>`
  - Khi kết hợp `--force-with-lease`: `git push -u origin <current_branch> --force-with-lease`
- **Dòng lệnh học tập:** `💻 Lệnh CLI: $ git push -u origin java/model/taskxxx`
- **Gợi ý quy trình (Tip Callout):**
  - Một khung viền vàng nhẹ với dòng chữ:
    *"💡 Mẹo quy trình: Sau khi push nhánh con lên origin thành công, hãy truy cập GitHub/GitLab tạo Pull Request để gộp nhánh này vào 'dev'!"*

---

### 3.6. Dòng Lệnh Học Tập (Command Preview / Learning Label)
- Dưới mỗi hàng thao tác trong từng Card, bố trí một nhãn hiển thị:
  - Font chữ: `UiHelper.FONT_CONSOLE` (Consolas/Cascadia Mono 13pt).
  - Màu sắc: Chữ xanh cyan `#78BEFF`, nền xám sẫm bo góc nhẹ dạng terminal chip:
    `💻 Lệnh vừa chạy / xem trước: $ git <command_args>`
  - Nhãn này cập nhật **ngay lập tức khi người dùng thay đổi dropdown/checkbox** (xem trước) và **xác nhận lại sau khi lệnh chạy thành công**.

---

### 3.7. Bảng Đen Console Log & Xử Lý Cuộn
- **Khung cuộn `JScrollPane`:**
  - Bọc toàn bộ vùng 4 Card bên trên.
  - Đặt `unitIncrement = 18` để cuộn bằng chuột mượt mà.
- **Bảng đen `LogConsole`:**
  - Tái sử dụng `envsetup.ui.LogConsole` (đã có tính năng chống nghẽn EDT bằng batching 60ms, tự động đổi màu theo cú pháp log, có nút Sao chép và Xoá).
  - Tích hợp nút `[Huỷ tiến trình]` trong trường hợp lệnh git bị treo (như chờ SSH passphrase hoặc timeout mạng).

---

## 4. Kiến Trúc Kỹ Thuật (Architecture & Classes)

```
src/envsetup/
├── runner/
│   ├── GitRunner.java          <-- [TẠO MỚI] Thực thi lệnh git CLI, stream log, parse branch
│   ├── PlatformUtil.java       <-- [GIỮ NGUYÊN] Thư mục gốc dự án & OS detection
│   └── ScriptRunner.java       <-- [GIỮ NGUYÊN] Quản lý tiến trình & Process handle
└── ui/
    ├── GitTab.java             <-- [TẠO MỚI] Giao diện Tab Git, 4 Cards, JScrollPane, Console
    ├── MainWindow.java         <-- [CHỈNH SỬA] Thêm tab "  4. Git  " vào JTabbedPane
    ├── Icons.java              <-- [BỔ SUNG] Thêm icon Java2D: BRANCH, REFRESH, GIT_MERGE
    ├── UiHelper.java           <-- [TẬN DỤNG] Màu sắc, font chữ, FlatButton, CardPanel
    └── LogConsole.java         <-- [TẬN DỤNG] Bảng đen terminal
```

### 4.1. Lớp Mới: `src/envsetup/runner/GitRunner.java`
Chịu trách nhiệm tương tác cấp thấp với git CLI:
- `static boolean isGitInstalled()`: Kiểm tra `git --version`.
- `static String getCurrentBranch()`: Gọi `git rev-parse --abbrev-ref HEAD`.
- `static String getLatestCommitShort()`: Gọi `git log -1 --format="%h %s"`.
- `static List<String> getLocalBranches()`: Gọi `git branch --format="%(refname:short)"`.
- `static List<String> getRemoteBranches()`: Gọi `git branch -r --format="%(refname:short)"` (lọc bỏ `origin/HEAD`).
- `static boolean hasUpstream(String branch)`: Kiểm tra `git rev-parse --verify @{u}`.
- `static ScriptRunner.Handle execute(List<String> command, String displayLabel, LogConsole console, Consumer<Integer> onFinished)`:
  - Khởi tạo tiến trình qua `ProcessBuilder`.
  - Thiết lập working directory là `PlatformUtil.getProjectRoot()`.
  - Stream `stdout` và `stderr` đồng thời về `console` theo UTF-8.
  - Trả về `ScriptRunner.Handle` để hỗ trợ hủy lệnh khi cần.

### 4.2. Lớp Mới: `src/envsetup/ui/GitTab.java`
- Kế thừa `JPanel`, sử dụng `BorderLayout`.
- Bố cục:
  - `NORTH`: Top Info Bar (`currentBranchLabel`, `commitLabel`, `btnRefresh`).
  - `CENTER`: `JSplitPane` (chiều dọc):
    - Nửa trên: `JScrollPane` chứa `contentPanel` (Vertical `BoxLayout` gồm 4 Stage Cards).
    - Nửa dưới: `console` (`LogConsole`).
  - `SOUTH`: Status Bar hiển thị trạng thái và nút Huỷ tiến trình.
- Quản lý trạng thái:
  - Hàm `refreshBranches()`: Cập nhật lại danh sách các ComboBox và tự động chọn branch mặc định.
  - Hàm `setControlsEnabled(boolean)`: Tạm khóa toàn bộ nút khi đang có lệnh git đang chạy để tránh xung đột file index lock (`.git/index.lock`).

### 4.3. Chỉnh Sửa `src/envsetup/ui/MainWindow.java`
- Thêm tab thứ 4 vào `JTabbedPane`:
  ```java
  tabs.addTab("  1. Chung  ", new GeneralTab());
  tabs.addTab("  2. Java  ", new JavaTab());
  tabs.addTab("  3. SQL  ", new SqlTab());
  tabs.addTab("  4. Git  ", new GitTab());
  ```

---

## 5. Danh Sách Công Việc & Kịch Bản Kiểm Thử

### 5.1. Các bước triển khai cho AI tiếp theo
1. **Bước 1:** Bổ sung các icon Java2D vào `src/envsetup/ui/Icons.java` (icon `BRANCH`, `REFRESH`, `MERGE` nếu cần).
2. **Bước 2:** Xây dựng `src/envsetup/runner/GitRunner.java` với đầy đủ các phương thức đọc nhánh và chạy lệnh bất đồng bộ.
3. **Bước 3:** Xây dựng `src/envsetup/ui/GitTab.java` với đầy đủ 4 Card, liên kết ComboBox, CheckBox, Preview command và `LogConsole`.
4. **Bước 4:** Đăng ký `GitTab` trong `src/envsetup/ui/MainWindow.java`.
5. **Bước 5:** Biên dịch và đóng gói lại `SetupApp.jar` thông qua script `.\scripts\ps1\build-setup.ps1` (hoặc `build-setup.sh`).

### 5.2. Kịch bản kiểm thử (Verification Checklist)
- [ ] **Khởi động ứng dụng:** Mở `SetupApp.jar` (hoặc `Setup.bat`), chuyển sang tab "4. Git". Nhánh hiện tại hiển thị đúng `main`.
- [ ] **Kiểm tra `git status`:** Bấm nút, console hiển thị đúng trạng thái sạch/bẩn của working tree; nhãn CLI hiển thị `$ git status`.
- [ ] **Kiểm tra `git fetch`:**
  - Chọn nhánh `origin/main` -> console hiển thị `git fetch origin main`.
  - Tick chọn `All branches` -> Dropdown mờ đi -> console hiển thị `git fetch --all --prune`.
- [ ] **Kiểm tra `git switch`:** Dropdown hiển thị các nhánh local; chọn nhánh và bấm chuyển -> Badge nhánh trên cùng tự động đổi theo.
- [ ] **Kiểm tra `git pull`:** Mặc định tick `--ff-only` -> chạy `git pull --ff-only`.
- [ ] **Kiểm tra `git merge` (3 Menu):**
  - Menu 1 chọn `origin` -> Menu 2 hiện `origin/dev`, `origin/main`...
  - Menu 1 chọn `local` -> Menu 2 hiện `dev`, `main`...
  - Ký hiệu mũi tên hiển thị rõ ràng hướng merge.
  - Lệnh CLI thực thi đúng nhánh nguồn đã chọn.
- [ ] **Kiểm tra `git push`:**
  - Tự động nhận diện nhánh hiện tại.
  - Tick `-u origin <branch>` khi nhánh mới chưa có upstream.
  - Dòng lệnh preview hiển thị chính xác cú pháp.
- [ ] **Kiểm tra cuộn giao diện:** Thu nhỏ cửa sổ MainWindow -> Khung cuộn `JScrollPane` hoạt động mượt mà, không nút nào bị tràn hay mất hiển thị.
- [ ] **Kiểm tra đóng gói:** Chạy `.\scripts\ps1\build-setup.ps1` kết thúc với mã thoát 0 và tạo ra `SetupApp.jar` chuẩn tại thư mục gốc.

---

## 6. Trạng Thái Triển Khai (đã hoàn thành)

### 6.1. File đã thay đổi
| File | Trạng thái | Ghi chú |
|------|-----------|---------|
| `src/envsetup/runner/GitRunner.java` | Tạo mới | Chạy lệnh git CLI: truy vấn đồng bộ + chạy chuỗi lệnh bất đồng bộ |
| `src/envsetup/ui/GitTab.java` | Tạo mới | Tab Git: thanh trạng thái, 4 thẻ giai đoạn, khung cuộn, bảng log |
| `src/envsetup/ui/Icons.java` | Bổ sung | Thêm 7 hình vẽ Java2D: BRANCH, REFRESH, MERGE, DOWNLOAD, UPLOAD, SWITCH, INFO |
| `src/envsetup/ui/MainWindow.java` | Chỉnh sửa | Đăng ký tab `  4. Git  ` |
| `SetupApp.jar` | Build lại | Qua `scripts\ps1\build-setup.ps1`, mã thoát 0 |

> Lưu ý: `.gitignore` dòng 30 loại trừ `src/envsetup/`, nên mã nguồn tab Git **không được commit**;
> các thành viên chỉ nhận bản `SetupApp.jar` đã build sẵn ở thư mục gốc.

### 6.2. Những điểm làm khác kế hoạch (và lý do)
1. **Bỏ toàn bộ emoji (🌿 📌 💻 ➔).** `Icons.java` đã ghi rõ: font hệ thống Windows (Segoe UI)
   không có glyph emoji nên Swing vẽ ra ô vuông rỗng. Thay bằng icon Java2D và ký hiệu ASCII
   `--- GỘP VÀO --->`.
2. **`GitTab` không kế thừa `BaseTab`.** `BaseTab` gắn chặt với việc chạy file script trong
   `scripts/`, trong khi tab Git ghép lệnh động từ lựa chọn giao diện. Tab dùng lại `LogConsole`
   và tự quản lý nút Huỷ, đồng hồ đếm giờ.
3. **Chặn git treo vì hỏi mật khẩu.** `GitRunner` đặt `GIT_TERMINAL_PROMPT=0`, `GIT_ASKPASS=""`,
   `SSH_ASKPASS=""` và `GIT_PAGER=cat`. Nếu không, fetch/pull/push tới remote HTTPS chưa lưu
   credential sẽ chờ nhập liệu ở stdin (mà app đã đóng stdin) và treo vĩnh viễn.
4. **Đọc nhánh remote bằng `%(refname)` thay vì `%(refname:short)`.** Git rút gọn
   `refs/remotes/origin/HEAD` thành đúng chữ `origin`, lọt vào dropdown thành một mục rác
   không merge được. Lỗi này đã tái hiện thật trên kho dự án trước khi sửa.
5. **Cảnh báo khi working tree còn thay đổi chưa commit** trước khi `switch`/`merge`, và cảnh báo
   riêng khi `--force-with-lease` trên `dev`/`main`. Đây là hai tình huống làm người mới mất code.
6. **Ô lệnh CLI bấm được để sao chép** vào clipboard, thay vì chỉ hiển thị.
7. **Mọi truy vấn git chạy ở luồng nền**, cập nhật giao diện qua `invokeLater`. Chạy thẳng trên EDT
   sẽ làm cửa sổ đứng hình mỗi lần chuyển nhánh.
8. **Chuỗi lệnh ghép dừng ngay khi một lệnh lỗi** (ví dụ `git switch <đích> && git merge <nguồn>`),
   tránh merge nhầm vào nhánh đang đứng khi switch thất bại.

### 6.3. Kết quả kiểm thử đã chạy
- [x] Biên dịch toàn bộ `src/envsetup` không lỗi; đóng gói `SetupApp.jar` mã thoát 0.
- [x] Truy vấn git trên kho thật: nhánh hiện tại, commit mới nhất, nhánh local, nhánh remote,
      upstream, trạng thái working tree — đều đúng.
- [~] Render giao diện 4 thẻ giai đoạn: nút, checkbox, dropdown, ô lệnh CLI hiển thị đầy đủ,
      khung cuộn hoạt động, không tràn ngang.
      **Kết luận này sai**: chỉ nhìn bằng mắt nên không phát hiện các thẻ bị hụt chiều cao,
      ô lệnh CLI cuối thẻ bị viền cắt ngang. Xem mục 7.
- [x] Dòng lệnh xem trước sinh đúng: `git status`, `git fetch origin main`, `git switch main`,
      `git pull --ff-only`, `git merge origin/main`, `git push origin main`.
- [x] Chuỗi lệnh lỗi: `git switch <nhánh không tồn tại> && git merge origin/main` dừng đúng sau
      lệnh đầu (mã thoát 128), không chạy lệnh merge.
- [x] **Đã kiểm thử với remote có nhiều nhánh** bằng kho tạm dựng riêng cho việc này — xem mục 7.2.

---

## 7. Rà Soát Lại & Sửa Lỗi (lần 2)

Người dùng phát hiện **ô lệnh CLI bị viền dưới của thẻ giai đoạn cắt ngang** ("tràn box").
Rà soát lại toàn bộ tab Git bằng cách in kích thước thật của từng component lúc chạy
(`getBounds()` so với `getPreferredSize()`) và chụp lại giao diện ở 100% lẫn 125% DPI.

### 7.1. Lỗi đã sửa

| # | Lỗi | Nguyên nhân | Cách sửa |
|---|-----|-------------|----------|
| 1 | Ô lệnh CLI cuối mỗi thẻ bị viền thẻ cắt ngang, thẻ thiếu 10-30px chiều cao | `fullWidth()` gọi `setMaximumSize()` **một lần lúc dựng giao diện**, khi đó ô lệnh còn rỗng và ComboBox chưa có dữ liệu. BoxLayout lấy tổng chiều cao tối đa của các hàng làm chiều cao tối đa của thẻ nên thẻ ngắn hơn nội dung thật | Thay bằng lớp `GitTab.Row`, tính `getMaximumSize()` động theo `getPreferredSize()` mỗi lần được hỏi |
| 2 | ComboBox chỉ cao 19px, nút mũi tên bị bóp méo, viền vẽ lệch | `JComboBox` tính chiều cao mong muốn đúng bằng một dòng chữ, trong khi `XPComboBoxButton` của look-and-feel Windows cần 21px | Thêm `UiHelper.createComboBox()` nâng chiều cao tối thiểu lên `fontHeight + 12` (~28px, tự co giãn theo DPI) và đặt font đồng bộ với các nhãn |
| 3 | Dòng trạng thái mất kết quả lệnh vừa chạy sau vài trăm ms | Chạy xong lệnh nào cũng gọi `refreshAll()`, mà bước nạp nhánh ghi đè `lblStatus` thành "Sẵn sàng. Đã nạp..." | Giữ kết quả ở `lastResult`, chỉ xoá khi người dùng tự bấm "Làm mới" |
| 4 | Bấm ô lệnh CLI hai lần liên tiếp thì đuôi "[đã sao chép]" dính lại vĩnh viễn | Timer khôi phục lại `getText()` đã chụp ở lần bấm trước (lúc đó đã chứa sẵn đuôi này) | Dựng lại chuỗi từ `plainText` thay vì nhớ `getText()` |
| 5 | Tiêu đề commit dài làm hỏng thanh thông tin đầu trang | Nhãn commit nằm trong `FlowLayout`, hết chỗ thì bị đẩy xuống dòng và viền thẻ cắt mất | Đưa nhãn commit vào `BorderLayout.CENTER` (tự rút gọn theo chỗ trống), giữ bản đầy đủ ở tooltip |
| 6 | Đường dẫn dự án sâu đè lên dòng phiên bản Java ở thanh trạng thái cửa sổ | Nhãn ở `BorderLayout.WEST` luôn được cấp trọn bề rộng nó xin | Chuyển sang `BorderLayout.CENTER` (`MainWindow`) |
| 7 | `GitRunner.capture()` không thật sự có hạn chờ 15 giây | Đọc hết output rồi mới `waitFor(timeout)`, nên `readLine()` chặn vô hạn nếu git treo -> tab Git kẹt ở "Đang đọc thông tin kho git..." | Đọc output ở luồng riêng, `waitFor` chạy song song và `destroyForcibly()` khi quá hạn |
| 8 | `FlatButton` khai báo `maximumSize` (26px) nhỏ hơn `preferredSize` (28-34px) | Kế thừa mặc định từ `BasicButtonUI` | Override `getMaximumSize()` trả về `getPreferredSize()` |

### 7.2. Kiểm thử đã chạy lại
- [x] In kích thước toàn bộ cây component ở 1050x720 và 880x620 (kích thước nhỏ nhất):
      không còn hàng hay thẻ nào nhỏ hơn kích thước mong muốn.
- [x] Chụp giao diện thật ở 100% và 125% DPI: ô lệnh CLI nằm trọn trong thẻ, ComboBox vẽ đúng.
- [x] Câu lệnh xem trước đúng với mọi tổ hợp checkbox/dropdown (`--all --prune`, `--ff-only`,
      `-u`, `--force-with-lease`, `--tags`, nguồn `origin`/`local`).
- [x] **Đã kiểm thử với kho nhiều nhánh** (mục còn treo ở 6.3): tạo kho tạm có `dev`, `main`,
      `java/model/task101`, `sql/table/task202` và một remote thật.
      Mặc định đúng đặc tả: fetch `origin/<nhánh hiện tại>`, switch về `dev`,
      merge `origin/dev` vào nhánh đang đứng; `origin/HEAD` không lọt vào danh sách.
- [x] Chạy thật qua nút bấm trên kho tạm: `git switch` đổi badge nhánh; `git merge` sang nhánh
      khác tự chèn `git switch <đích>` trước, log ghi đủ hai lệnh và `[XONG]`.
- [x] Ba tab còn lại (Chung, Java, SQL) không bị ảnh hưởng bởi thay đổi ở `UiHelper`.
