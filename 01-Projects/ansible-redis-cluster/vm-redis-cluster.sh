#!/bin/bash
# 优化版：创建Redis集群VM
# 使用方法：在PVE节点上运行

set -e

# 配置
CLOUD_IMAGE="/var/lib/vz/template/iso/jammy-server-cloudimg-amd64.img"
STORAGE="local-lvm"
BRIDGE="vmbr1"
GATEWAY="10.0.0.1"
DNS="10.0.0.1"

# SSH公钥（用于cloud-init自动配置）
# 请替换为你自己的公钥
SSH_PUBLIC_KEY="ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQCyMzIVq/0vTdNxnW/gAzSq5SmkASNzE+uMu6ITGF9S/41i5C5pX/7b9lgN+wNulOuI0/XwMSIjHSiqwUsgRZoeIbHGq6UiMoMHwkKDCWBgGjLViniUGTaXg0MaWk6OkmjlARr5ohQEAWmYEwGF+01EK6x8IntIk095XIFaHopJEUfTz0AbgHzQKPbo6uFlp5iZsYwdxuFxkOlxnlmjEOYU1mvvdTBiCu95UcrvzqKxxY4DFlFe4oDPdM0tdgmx3sn0CAdhx7SMmTDqBbrs97ooHIXXa4B0FWlHJLSYWdS2NSmOu9k4zel+J1Yb/OJwzHlI0vNVh+KreZNjdMyYFgaZc4WfAdr94/Rs6XbWvOYCeN3pb43T8ZXnQn/vJDTkGaTtCbCZWyxp5C9nfa2KCUSN5iwpuG7w9kZrPu8rvHoomo0KXerbAcqHyWfnUfBAbp4ph/lss8UQ0RCRpjrnhjQ86Wg3OPOnGhnVsX3WsYEL2Q7sASM5xmVb30w28JTH0fZQad2b1WVdJQqyEgMzpp/Z8qJuz0HJ8qs6xsHkAt5WMwVbHWz/r956GxiYrp3x2ZRTHQGK1qMdO0Q4WYuIzBbFkj0UnNj2NxWQj2KVG1m7aKRum2NZsFfu4MFhRU6/K/hkgCqbFlM73V03P9JqiKzdbDxGrFPqtLZ123YR4eomsQ== ansible@local"

# 下载Cloud Image（如果不存在）
download_image() {
    if [ ! -f "$CLOUD_IMAGE" ]; then
        echo "下载Ubuntu Cloud Image..."
        mkdir -p /var/lib/vz/template/iso
        cd /var/lib/vz/template/iso
        wget https://cloud-images.ubuntu.com/jammy/current/jammy-server-cloudimg-amd64.img
        echo "下载完成"
    else
        echo "Cloud Image已存在"
    fi
}

# 创建单个VM
create_redis_vm() {
    local VMID=$1
    local NAME=$2
    local IP=$3
    local CPU=$4
    local MEM=$5
    local DISK=$6

    echo "=========================================="
    echo "创建 $NAME (VM $VMID)..."
    echo "=========================================="

    # 如果VM已存在，先销毁
    if qm status $VMID &>/dev/null; then
        echo "VM $VMID 已存在，正在销毁..."
        qm stop $VMID 2>/dev/null || true
        sleep 2
        qm destroy $VMID
    fi

    # 创建VM（使用q35芯片组和VirtIO SCSI）
    qm create $VMID \
        --name $NAME \
        --memory $MEM \
        --cores $CPU \
        --cpu host \
        --machine q35 \
        --scsihw virtio-scsi-single \
        --net0 virtio,bridge=$BRIDGE,firewall=1 \
        --agent enabled=1

    # 导入磁盘
    qm importdisk $VMID $CLOUD_IMAGE $STORAGE
    qm set $VMID --scsi0 ${STORAGE}:vm-${VMID}-disk-0,ssd=1,discard=on,iothread=1

    # 配置Cloud-Init
    qm set $VMID --ide2 ${STORAGE}:cloudinit
    qm set $VMID --boot order=scsi0
    qm set $VMID --ciuser redis
    qm set $VMID --cipassword redis123456
    qm set $VMID --ipconfig0 ip=${IP}/24,gw=$GATEWAY
    qm set $VMID --nameserver $DNS
    qm set $VMID --sshkeys <(echo "$SSH_PUBLIC_KEY")

    # 调整磁盘大小
    qm resize $VMID scsi0 ${DISK}G

    echo "$NAME 创建完成，IP: $IP"
    echo ""
}

# 批量启动VM
start_all_vms() {
    echo "启动所有Redis VM..."
    for vmid in 102 103 104 105 106 107; do
        echo "  启动 VM $vmid..."
        qm start $vmid 2>/dev/null || echo "  VM $vmid 启动失败或已在运行"
    done
    echo "启动命令已发送"
}

# 批量停止VM
stop_all_vms() {
    echo "停止所有Redis VM..."
    for vmid in 102 103 104 105 106 107; do
        echo "  停止 VM $vmid..."
        qm stop $vmid 2>/dev/null || echo "  VM $vmid 停止失败或已停止"
    done
    echo "停止命令已发送"
}

# 批量销毁VM
destroy_all_vms() {
    echo "警告：这将销毁所有Redis VM！"
    read -p "确认销毁？(yes/no): " confirm
    if [ "$confirm" = "yes" ]; then
        for vmid in 102 103 104 105 106 107; do
            echo "  销毁 VM $vmid..."
            qm stop $vmid 2>/dev/null || true
            sleep 1
            qm destroy $vmid 2>/dev/null || echo "  VM $vmid 不存在"
        done
        echo "销毁完成"
    else
        echo "取消销毁"
    fi
}

# 查看所有VM状态
status_all_vms() {
    echo "=========================================="
    echo "Redis集群VM状态"
    echo "=========================================="
    printf "%-10s %-15s %-10s %-15s\n" "VMID" "名称" "状态" "IP地址"
    echo "------------------------------------------"
    for vmid in 102 103 104 105 106 107; do
        if qm config $vmid &>/dev/null; then
            name=$(qm config $vmid | grep '^name:' | cut -d' ' -f2-)
            status=$(qm status $vmid 2>/dev/null | grep 'status:' | awk '{print $2}')
            ip=$(qm guest cmd $vmid network-get-interfaces 2>/dev/null | grep -oE '10\.0\.0\.[0-9]+' | head -1 || echo "N/A")
            printf "%-10s %-15s %-10s %-15s\n" "$vmid" "$name" "$status" "$ip"
        else
            printf "%-10s %-15s %-10s %-15s\n" "$vmid" "不存在" "-" "-"
        fi
    done
}

# 显示帮助信息
show_help() {
    echo "Redis集群VM管理脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  create   创建所有Redis VM（默认）"
    echo "  start    启动所有VM"
    echo "  stop     停止所有VM"
    echo "  restart  重启所有VM"
    echo "  destroy  销毁所有VM（需确认）"
    echo "  status   查看所有VM状态"
    echo "  help     显示此帮助信息"
    echo ""
    echo "VM配置:"
    echo "  Master节点: 102-104 (2核/2G内存/20G磁盘)"
    echo "  Slave节点:  105-107 (1核/1G内存/10G磁盘)"
    echo ""
    echo "网络配置:"
    echo "  网桥: $BRIDGE"
    echo "  网关: $GATEWAY"
    echo "  IP段: 10.0.0.102-107"
    echo ""
    echo "登录信息:"
    echo "  用户名: redis"
    echo "  密码: redis123456"
    echo "  SSH密钥: 已自动配置"
    echo ""
    echo "示例:"
    echo "  $0 create   # 创建所有VM"
    echo "  $0 start    # 启动所有VM"
    echo "  $0 status   # 查看VM状态"
}

# 创建所有VM
create_all_vms() {
    download_image

    echo ""
    echo "=========================================="
    echo "开始创建Redis集群VM"
    echo "=========================================="
    echo ""

    # 创建3个Master节点
    create_redis_vm 102 "redis-master1" "10.0.0.102" 2 2048 20
    create_redis_vm 103 "redis-master2" "10.0.0.103" 2 2048 20
    create_redis_vm 104 "redis-master3" "10.0.0.104" 2 2048 20

    # 创建3个Slave节点
    create_redis_vm 105 "redis-slave1" "10.0.0.105" 1 1024 10
    create_redis_vm 106 "redis-slave2" "10.0.0.106" 1 1024 10
    create_redis_vm 107 "redis-slave3" "10.0.0.107" 1 1024 10

    echo ""
    echo "=========================================="
    echo "所有VM创建完成！"
    echo "=========================================="
    echo ""
    echo "下一步操作："
    echo "  1. 启动所有VM: $0 start"
    echo "  2. 查看状态:   $0 status"
    echo ""
    echo "登录信息："
    echo "  用户名: redis"
    echo "  密码: redis123456"
    echo "  SSH密钥已自动配置"
    echo ""
}

# 主菜单
case "${1:-create}" in
    create)
        create_all_vms
        ;;
    start)
        start_all_vms
        ;;
    stop)
        stop_all_vms
        ;;
    destroy)
        destroy_all_vms
        ;;
    status)
        status_all_vms
        ;;
    restart)
        stop_all_vms
        sleep 3
        start_all_vms
        ;;
    help|-h|--help)
        show_help
        ;;
    *)
        echo "未知命令: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
