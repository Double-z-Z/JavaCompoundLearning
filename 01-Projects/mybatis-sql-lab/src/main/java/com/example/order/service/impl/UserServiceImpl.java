package com.example.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.order.model.User;
import com.example.order.mp.MpUserMapper;
import com.example.order.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<MpUserMapper, User> implements UserService {
}
