# Roadmap học Linux trong 16 tuần

Lộ trình này dành cho developer backend, DevOps beginner và người muốn hiểu Linux đủ sâu để chạy ứng dụng, debug server, dùng Docker và triển khai Spring Boot.

Thời lượng đề xuất: **8–12 giờ/tuần** trong 16 tuần.

Nên dùng một máy ảo Ubuntu Server LTS hoặc Ubuntu trên WSL2 để thực hành. Không thử lệnh phá hủy trên máy chính. Tài liệu Ubuntu Server có các phần riêng về networking, storage, security, virtualization và system tuning; tài liệu kernel là nguồn tham khảo cho procfs, VFS và internals. [Ubuntu Server docs](https://ubuntu.com/server/docs/explanation/) · [Linux kernel documentation](https://docs.kernel.org/)

## Mục tiêu cuối lộ trình

Sau 16 tuần, bạn có thể:

- Sử dụng terminal và shell thành thạo.
- Hiểu Linux filesystem, process, permission và package.
- Quản lý user, group, service và systemd.
- Debug CPU, memory, disk, network và application process.
- Cấu hình SSH an toàn.
- Hiểu TCP/IP, DNS, port, routing và firewall.
- Quản lý disk, partition, filesystem, mount và backup.
- Viết Bash script có kiểm tra lỗi.
- Tự động hóa bằng cron, systemd timer và SSH.
- Đọc log và điều tra incident.
- Hardening server ở mức thực tế.
- Chạy ứng dụng Java/Spring Boot bằng systemd hoặc Docker.
- Viết runbook vận hành và hoàn thành capstone server.

## Môi trường học

Chọn một:

- Ubuntu Server LTS trên VirtualBox/VMware.
- Ubuntu trên WSL2.
- Một VPS nhỏ dùng riêng cho lab.
- Docker container để luyện command không cần systemd.

Khuyến nghị máy ảo:

| Thành phần | Mức khuyến nghị |
|---|---|
| CPU | 2 vCPU |
| RAM | 4 GB |
| Disk | 25 GB |
| Network | NAT, thêm host-only nếu cần |

Tạo snapshot trước các bài storage, firewall, user privilege và boot troubleshooting.

---

# Giai đoạn 1 — Terminal và filesystem

## Tuần 1: Linux mindset và command line

### Học

- Kernel, user space và shell.
- Distribution, package manager và release.
- Terminal, TTY, stdin, stdout, stderr.
- Command exit status.
- Absolute path và relative path.
- Environment variable.
- PATH.
- Man page và help.
- Alias và history.

### Lệnh cần nắm

~~~bash
pwd
ls -lah
cd
man ls
command -v java
echo "$PATH"
history
whoami
id
uname -a
hostnamectl
~~~

### Bài tập

- Tạo workspace cho từng tuần.
- Tìm binary của java, ssh, systemctl.
- Đọc man page của ls, find, chmod và systemctl.
- Kiểm tra exit code sau command thành công và thất bại.

### Tiêu chí hoàn thành

- Không phụ thuộc GUI cho thao tác file cơ bản.
- Biết khi nào lỗi nằm ở command, PATH hoặc permission.
- Biết đọc phần SYNOPSIS và OPTIONS trong man page.

## Tuần 2: Filesystem hierarchy và file operations

### Học

- /, /bin, /sbin, /etc, /var, /usr, /home, /tmp, /opt, /proc, /sys.
- File, directory, symbolic link, hard link.
- inode.
- File descriptor.
- Hidden file.
- Glob pattern.
- cp, mv, rm, mkdir, touch.
- ln, readlink, realpath.
- stat, file, tree.

### Thực hành

~~~bash
mkdir -p ~/linux-lab/{data,logs,scripts,backup}
touch ~/linux-lab/data/app.log
ln -s ~/linux-lab/data/app.log ~/linux-lab/current.log
stat ~/linux-lab/data/app.log
find ~/linux-lab -type f -name "*.log"
~~~

### Cảnh báo

- Không chạy rm -rf với path chưa kiểm tra.
- Luôn dùng pwd và ls trước thao tác xóa.
- Không xóa /var/lib hoặc /etc trên máy thật khi chưa hiểu tác động.

## Tuần 3: Text processing và pipeline

### Học

- cat, less, head, tail.
- grep và regular expression cơ bản.
- cut, sort, uniq, tr.
- wc, xargs.
- sed.
- awk.
- Pipe.
- Redirection.
- tee.
- Here document.
- Exit status trong pipeline.

### Bài tập

- Đếm HTTP status trong access log.
- Tìm request chậm hơn ngưỡng.
- Nhóm top IP.
- Lấy cột cần thiết từ file CSV.
- Dùng tail -f để theo dõi log.

~~~bash
grep " 500 " access.log | awk '{print $7}' | sort | uniq -c | sort -nr
tail -f app.log
du -sh /var/log/*
~~~

### Tiêu chí hoàn thành

- Ghép được nhiều command nhỏ thành pipeline.
- Biết quote string có khoảng trắng.
- Biết tránh parse output không ổn định khi có format máy đọc được.

---

# Giai đoạn 2 — Permission, user và process

## Tuần 4: User, group và permissions

### Học

- UID và GID.
- /etc/passwd, /etc/shadow, /etc/group.
- Root.
- sudo.
- User primary group và supplementary group.
- Read, write, execute.
- Permission theo owner, group, other.
- chmod symbolic và numeric.
- chown, chgrp.
- umask.
- setuid, setgid, sticky bit.
- ACL ở mức cơ bản.

### Thực hành

~~~bash
sudo useradd --create-home appuser
sudo groupadd appteam
sudo usermod --append --groups appteam appuser
sudo chown -R appuser:appteam /opt/app
sudo chmod 750 /opt/app
umask
getfacl /opt/app
~~~

### Bài tập

- Tạo user chỉ chạy service.
- Tạo thư mục team share.
- Ngăn user đọc secret.
- Cho phép app ghi logs nhưng không sửa binary.

### Nguyên tắc

- Không chạy ứng dụng bằng root nếu không cần.
- Sudo theo principle of least privilege.
- Quyền write thường nguy hiểm hơn quyền read.
- Kiểm tra cả quyền trên từng directory trong path.

## Tuần 5: Process, job và signal

### Học

- PID và PPID.
- Process state.
- Foreground/background.
- jobs, bg, fg, nohup.
- ps, pgrep, pidof.
- top, htop.
- kill và signal.
- SIGTERM, SIGKILL, SIGHUP, SIGINT.
- Zombie và orphan process.
- Nice và renice.
- File descriptor.
- /proc/PID.

### Thực hành

~~~bash
ps aux --forest
pgrep -af java
top
kill -TERM <pid>
ls -l /proc/<pid>/fd
cat /proc/meminfo
cat /proc/loadavg
~~~

procfs cung cấp thông tin về process, memory, CPU, network và kernel state; đây là nguồn dữ liệu quan trọng khi debug Linux. [proc filesystem](https://docs.kernel.org/filesystems/proc.html)

### Bài tập

- Tìm process chiếm CPU cao.
- Tìm process giữ file log.
- Dừng app bằng SIGTERM rồi kiểm tra graceful shutdown.
- Tạo process zombie trong lab và giải thích nguyên nhân.

## Tuần 6: Package management và environment

### Học

- apt update, upgrade, install, remove.
- Package repository.
- dpkg.
- Package dependency.
- Version pinning ở mức cơ bản.
- Environment variable.
- /etc/profile, ~/.profile, ~/.bashrc.
- Locale và timezone.
- timedatectl.
- Process environment trong /proc.

### Thực hành

~~~bash
sudo apt update
apt-cache policy nginx
dpkg -l | grep nginx
printenv
timedatectl
systemctl status systemd-timesyncd
~~~

### Cần tránh

- Cài package từ script không rõ nguồn.
- Trộn repository không tương thích.
- Dùng curl pipe vào shell mà chưa đọc script.
- Đặt secret trong .bashrc nếu nhiều process/user có thể đọc.

---

# Giai đoạn 3 — Service, log và automation

## Tuần 7: systemd và service management

### Học

- systemd.
- Unit.
- Service unit.
- Target.
- Socket unit ở mức nhận biết.
- systemctl start, stop, restart, enable, disable.
- Service dependency.
- Restart policy.
- EnvironmentFile.
- User service.
- Boot target.
- systemd timer.

### Tạo service Spring Boot

~~~ini
[Unit]
Description=Task API
After=network-online.target

[Service]
User=appuser
WorkingDirectory=/opt/task-api
ExecStart=/usr/bin/java -jar /opt/task-api/app.jar
Restart=on-failure
EnvironmentFile=/etc/task-api/task-api.env

[Install]
WantedBy=multi-user.target
~~~

### Lệnh

~~~bash
sudo systemctl daemon-reload
sudo systemctl enable --now task-api
systemctl status task-api
systemctl cat task-api
systemctl is-enabled task-api
~~~

### Tiêu chí hoàn thành

- Deploy được một app JAR bằng systemd.
- Service tự restart khi process lỗi.
- App chạy bằng non-root user.
- Biết xem dependency và status.

## Tuần 8: journald và log management

### Học

- stdout/stderr logging.
- journald.
- journalctl.
- Log level.
- Log rotation.
- rsyslog ở mức nhận biết.
- Structured log.
- Timestamp và timezone.
- Correlation ID.
- Disk usage của log.

### Thực hành

~~~bash
journalctl -u task-api
journalctl -u task-api -f
journalctl -u task-api --since "1 hour ago"
journalctl -p warning
journalctl --disk-usage
~~~

### Bài tập

- Tìm lần deploy gần nhất.
- Lọc lỗi 500 trong log.
- Giới hạn retention trong lab.
- Viết runbook khi disk đầy vì log.

## Tuần 9: Bash scripting

### Học

- Shebang.
- Variable.
- Quoting.
- Positional arguments.
- if, case, for, while.
- Function.
- Exit code.
- set -euo pipefail.
- Trap.
- Temporary file.
- Command substitution.
- Input validation.
- ShellCheck.

### Script bắt buộc

- backup.sh.
- healthcheck.sh.
- deploy.sh.
- cleanup-old-logs.sh.
- report-disk.sh.

### Mẫu an toàn

~~~bash
#!/usr/bin/env bash
set -euo pipefail

readonly BACKUP_DIR="/var/backups/task-api"
mkdir -p "$BACKUP_DIR"

if ! systemctl is-active --quiet task-api; then
  echo "task-api is not running" >&2
  exit 1
fi
~~~

### Cần tránh

- Không quote biến.
- Dùng eval.
- Dùng rm với wildcard chưa kiểm tra.
- Nuốt exit code.
- Hardcode secret.

---

# Giai đoạn 4 — Networking và remote administration

## Tuần 10: Linux networking fundamentals

### Học

- NIC.
- MAC và IP.
- IPv4 subnet.
- IPv6 ở mức nhận biết.
- Gateway.
- Routing table.
- TCP và UDP.
- Port.
- DNS.
- Hostname.
- Loopback.
- Network namespace ở mức khái niệm.

### Lệnh

~~~bash
ip addr
ip route
ss -tulpn
ping 8.8.8.8
curl -v http://localhost:8080/actuator/health
dig example.com
resolvectl status
traceroute example.com
~~~

Ubuntu Server dùng Netplan cho cấu hình mạng; DNS có thể được quản lý qua systemd-resolved/resolvectl tùy môi trường. [Ubuntu networking](https://ubuntu.com/server/docs/how-to/networking/) · [Configuring networks](https://ubuntu.com/server/docs/explanation/networking/configuring-networks/)

## Tuần 11: SSH, file transfer và firewall

### Học

- OpenSSH client/server.
- Key pair.
- known_hosts.
- ssh-agent.
- ssh_config và sshd_config.
- scp.
- rsync.
- Port forwarding.
- Local và remote tunnel.
- UFW.
- nftables ở mức nhận biết.
- Principle of least exposure.

### Thực hành

~~~bash
ssh-keygen -t ed25519
ssh-copy-id appuser@server
ssh appuser@server
rsync -av --delete ./dist/ appuser@server:/opt/task-api/
sudo ufw default deny incoming
sudo ufw allow OpenSSH
sudo ufw allow 8080/tcp
sudo ufw enable
sudo ufw status verbose
~~~

### Hardening

- Tắt password login sau khi key login hoạt động.
- Không cho root SSH trực tiếp nếu không cần.
- Giới hạn user và source network.
- Dùng fail2ban hoặc cơ chế tương đương khi phù hợp.
- Giữ một session admin đang mở khi thay đổi firewall.

## Tuần 12: Network troubleshooting

### Học

- Connection refused.
- Timeout.
- DNS failure.
- Wrong route.
- Firewall drop.
- Port bind conflict.
- TLS certificate error.
- MTU ở mức nhận biết.
- tcpdump ở mức cơ bản.
- curl verbose.
- nc.

### Incident lab

- Service chỉ bind 127.0.0.1.
- Firewall chặn port.
- DNS trả sai record.
- App listen sai port.
- Security group cloud chặn traffic.
- Process chết nhưng port còn bị giữ tạm thời.

### Quy trình

1. Kiểm tra process.
2. Kiểm tra listening socket.
3. Kiểm tra bind address.
4. Kiểm tra local curl.
5. Kiểm tra DNS.
6. Kiểm tra route.
7. Kiểm tra firewall.
8. Kiểm tra remote path.

---

# Giai đoạn 5 — Storage, performance và security

## Tuần 13: Disk, filesystem và backup

### Học

- Block device.
- Partition.
- Filesystem.
- Mount point.
- fstab.
- ext4 và xfs ở mức nhận biết.
- df và du.
- Inode exhaustion.
- lsof.
- File deleted but open.
- Swap.
- LVM ở mức nhập môn.
- Tar, gzip và rsync.

### Lệnh

~~~bash
lsblk
df -h
df -i
du -xhd1 /
find /var/log -type f -size +100M
ls -l /proc/1/fd
lsof +L1
free -h
swapon --show
~~~

### Bài tập

- Tạo filesystem trên disk lab.
- Mount bằng UUID.
- Backup một thư mục.
- Restore sang thư mục mới.
- Mô phỏng disk đầy bằng file loop trong VM.

## Tuần 14: System performance

### Học

- Load average.
- CPU user/system/iowait.
- Memory available.
- Page cache.
- Swap.
- Disk I/O.
- Context switch.
- File descriptor limit.
- Process limit.
- cgroup ở mức khái niệm.
- vmstat, iostat, mpstat.
- free và sar ở mức nhận biết.

### Thực hành

~~~bash
uptime
free -h
vmstat 1
iostat -xz 1
mpstat -P ALL 1
ulimit -a
cat /proc/limits
~~~

### Bài tập

- Tạo CPU-bound process.
- Tạo memory pressure trong VM.
- Tạo disk write workload.
- Tìm bottleneck bằng metric thay vì đoán.

## Tuần 15: Linux security và hardening

### Học

- Patch management.
- Least privilege.
- sudo policy.
- SSH hardening.
- Firewall.
- File permission.
- AppArmor ở mức cơ bản.
- SELinux ở mức nhận biết.
- Audit log.
- Secret management.
- TLS certificate.
- Malware/process inspection.
- Security update và unattended-upgrades.

Ubuntu khuyến nghị cập nhật hệ thống, dùng user không phải root, firewall và SSH an toàn; AppArmor có thể giới hạn quyền của ứng dụng bị compromise. [Ubuntu security suggestions](https://ubuntu.com/server/docs/explanation/security/security_suggestions/)

### Hardening checklist

- Tạo user admin riêng, không dùng root hằng ngày.
- Tắt SSH password login khi có key.
- Chỉ mở port cần thiết.
- Update security package.
- Kiểm tra service đang enable.
- Xóa package/service không dùng.
- Dùng permission tối thiểu.
- Không lưu secret trong command history.
- Có backup và kiểm tra restore.

## Tuần 16: Automation, Docker và capstone

### Học

- SSH automation.
- Cron và systemd timer.
- Ansible ở mức nhập môn.
- Docker relationship với Linux process, namespace, cgroup và filesystem.
- systemd service so với Docker container.
- Log và healthcheck.
- Deployment rollback.

### Capstone: Deploy Spring Boot trên Linux

Triển khai một ứng dụng gồm:

~~~text
Ubuntu Server
  ├── OpenSSH
  ├── UFW
  ├── Nginx reverse proxy
  ├── Spring Boot service
  ├── PostgreSQL
  ├── Redis
  └── backup timer
~~~

### Yêu cầu

- User app riêng.
- Spring Boot chạy bằng systemd hoặc Docker.
- Nginx proxy vào app.
- Chỉ public 80/443 và SSH giới hạn.
- PostgreSQL không public Internet.
- Logs qua journald.
- Healthcheck endpoint.
- Backup database bằng timer.
- Monitoring CPU, memory, disk và service status.
- Có deploy script và rollback script.
- Có runbook cho app down, disk đầy, port lỗi và SSH failure.

---

# Lịch học mỗi tuần

| Buổi | Nội dung |
|---|---|
| Buổi 1 — 1,5 giờ | Đọc concept và man page |
| Buổi 2 — 2 giờ | Thực hành command trong VM |
| Buổi 3 — 2 giờ | Làm lab có lỗi cố ý |
| Buổi 4 — 2 giờ | Viết script hoặc runbook |
| Cuối tuần — 2–4 giờ | Project, review và snapshot |

Quy tắc học:

1. Thử trong VM, không thử lệnh phá hủy trên máy chính.
2. Ghi command, output và nguyên nhân lỗi.
3. Mỗi tuần tạo một incident và tự khôi phục.
4. Dùng man page trước khi tìm snippet trên Internet.
5. Luôn biết cách rollback thay đổi firewall, user, mount và service.

---

# Checklist sau 16 tuần

## Command line

- Điều hướng và quản lý file không cần GUI.
- Dùng pipeline, grep, awk, sed.
- Đọc log và lọc dữ liệu.
- Viết Bash script có exit code và input validation.

## System

- Tạo user/group và permission đúng.
- Chạy app bằng systemd.
- Đọc process và signal.
- Tìm CPU, memory, disk bottleneck.
- Kiểm tra /proc và file descriptor.

## Network

- Đọc IP, route, DNS và listening socket.
- Debug connection refused và timeout.
- Cấu hình SSH key.
- Cấu hình firewall an toàn.

## Storage

- Mount filesystem.
- Đọc df, du và inode.
- Tạo backup và restore.
- Nhận biết file deleted but open.

## Operations

- Deploy Spring Boot.
- Đọc journald.
- Tự động hóa bằng timer/cron.
- Viết incident runbook.
- Rollback deployment.

## Security

- Không dùng root cho app.
- Patch hệ thống.
- Chỉ mở port cần thiết.
- Không lưu secret trong repo/history.
- Có backup và kiểm tra restore.

---

# Nguồn học chính thức

- [Ubuntu Server documentation](https://ubuntu.com/server/docs/)
- [Ubuntu networking](https://ubuntu.com/server/docs/how-to/networking/)
- [Ubuntu security suggestions](https://ubuntu.com/server/docs/explanation/security/security_suggestions/)
- [Ubuntu storage documentation](https://ubuntu.com/server/docs/how-to/storage/)
- [Linux kernel documentation](https://docs.kernel.org/)
- [The proc filesystem](https://docs.kernel.org/filesystems/proc.html)
- [Linux man-pages project](https://man7.org/linux/man-pages/)
- [systemd documentation](https://systemd.io/)
- [OpenSSH manual pages](https://man.openbsd.org/ssh)
- [Bash Reference Manual](https://www.gnu.org/software/bash/manual/)
- [Docker Get Started](https://docs.docker.com/get-started/)

Linux giỏi không chỉ là nhớ nhiều command. Bạn cần hiểu process, filesystem, permission, network và service liên hệ với nhau thế nào; sau đó dùng số liệu và log để tìm nguyên nhân thay vì đoán.
