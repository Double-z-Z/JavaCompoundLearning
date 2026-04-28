# Ansible Redis Cluster

使用Ansible自动化部署Redis 6节点集群。

## 架构

- 3 Master: 10.0.0.102-104
- 3 Slave: 10.0.0.105-107

## 使用方法

```bash
# 测试连通性
ansible all -m ping

# 部署Redis
ansible-playbook site.yml
```

