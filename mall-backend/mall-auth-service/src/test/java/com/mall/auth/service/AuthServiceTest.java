package com.mall.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mall.auth.dto.LoginRequest;
import com.mall.auth.dto.MerchantApplyRequest;
import com.mall.auth.dto.RegisterRequest;
import com.mall.auth.entity.Merchant;
import com.mall.auth.entity.User;
import com.mall.auth.mapper.MerchantMapper;
import com.mall.auth.mapper.UserMapper;
import com.mall.auth.vo.LoginResponse;
import com.mall.common.BizException;
import com.mall.common.JwtUtil;
import com.mall.common.MallConstants;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private MerchantMapper merchantMapper;
    @Spy private JwtUtil jwtUtil = new JwtUtil("test-secret-key-at-least-32-chars-long!!!", 3600);
    @Spy private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private AuthService authService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration config = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "");
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, Merchant.class);
    }

    // ----------------------------------------------------------
    // 用户登录
    // ----------------------------------------------------------

    @Test
    void login_user_success() {
        User user = mockUser(1001L, "user1", passwordEncoder.encode("pass123"), 0, 0);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        LoginResponse resp = authService.login(req("user1", "pass123"));
        assertThat(resp.getType()).isEqualTo(MallConstants.TYPE_USER);
        assertThat(resp.getId()).isEqualTo(1001L);
        assertThat(resp.getToken()).isNotBlank();
    }

    @Test
    void login_admin_success() {
        User admin = mockUser(2001L, "admin", passwordEncoder.encode("admin123"), 1, 0);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);
        LoginResponse resp = authService.login(req("admin", "admin123"));
        assertThat(resp.getType()).isEqualTo(MallConstants.TYPE_ADMIN);
    }

    // ----------------------------------------------------------
    // 商家登录
    // ----------------------------------------------------------

    @Test
    void login_merchant_approved_success() {
        Merchant merchant = mockMerchant(3001L, "seller1", passwordEncoder.encode("m123"), 0, 1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(merchantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(merchant);
        LoginResponse resp = authService.login(req("seller1", "m123"));
        assertThat(resp.getType()).isEqualTo(MallConstants.TYPE_MERCHANT);
        assertThat(resp.getId()).isEqualTo(3001L);
    }

    @Test
    void login_merchant_notApproved_throws() {
        Merchant merchant = mockMerchant(3001L, "seller2", passwordEncoder.encode("m123"), 0, 0);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(merchantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(merchant);
        assertThatThrownBy(() -> authService.login(req("seller2", "m123")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("审核中");
    }

    // ----------------------------------------------------------
    // 密码/状态校验
    // ----------------------------------------------------------

    @Test
    void login_passwordWrong_throws() {
        User user = mockUser(1001L, "user1", passwordEncoder.encode("correct"), 0, 0);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        assertThatThrownBy(() -> authService.login(req("user1", "wrong")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("密码错误");
    }

    @Test
    void login_accountDisabled_throws() {
        User user = mockUser(1001L, "user1", passwordEncoder.encode("pass"), 0, 1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        assertThatThrownBy(() -> authService.login(req("user1", "pass")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("禁用");
    }

    @Test
    void login_accountNotFound_throws() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(merchantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> authService.login(req("ghost", "pass")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
    }

    // ----------------------------------------------------------
    // 注册
    // ----------------------------------------------------------

    @Test
    void register_usernameExists_throws() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        RegisterRequest req = regReq("user1", "pass123", "昵称", "13800000000");
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void register_success_insertsUser() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);
        RegisterRequest req = regReq("newuser", "pass123", "新用户", "13900000000");
        authService.register(req);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("newuser");
        assertThat(saved.getRole()).isEqualTo(MallConstants.TYPE_USER);
        assertThat(saved.getStatus()).isEqualTo(0);
    }

    // ----------------------------------------------------------
    // 商家入驻
    // ----------------------------------------------------------

    @Test
    void merchantApply_usernameExists_throws() {
        when(merchantMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        MerchantApplyRequest req = merchantReq("seller1", "m123", "店名", "13800000000", "联系人", "店铺");
        assertThatThrownBy(() -> authService.merchantApply(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void merchantApply_success_insertsMerchant() {
        when(merchantMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(merchantMapper.insert(any(Merchant.class))).thenReturn(1);
        MerchantApplyRequest req = merchantReq("newseller", "m123", "新商家", "13900000000", "张三", "新店");
        authService.merchantApply(req);
        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantMapper).insert(captor.capture());
        Merchant saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("newseller");
        assertThat(saved.getApplyStatus()).isEqualTo(0);
        assertThat(saved.getBalance()).isZero();
    }

    // ----------------------------------------------------------
    // helpers
    // ----------------------------------------------------------

    private LoginRequest req(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username); r.setPassword(password);
        return r;
    }

    private RegisterRequest regReq(String username, String password, String nickname, String phone) {
        RegisterRequest r = new RegisterRequest();
        r.setUsername(username); r.setPassword(password); r.setNickname(nickname); r.setPhone(phone);
        return r;
    }

    private MerchantApplyRequest merchantReq(String username, String password, String merchantName,
                                             String phone, String contact, String shopName) {
        MerchantApplyRequest r = new MerchantApplyRequest();
        r.setUsername(username); r.setPassword(password); r.setMerchantName(merchantName);
        r.setPhone(phone); r.setContact(contact); r.setShopName(shopName);
        return r;
    }

    private User mockUser(Long id, String username, String encodedPassword, int role, int status) {
        User user = new User();
        user.setId(id); user.setUsername(username); user.setPassword(encodedPassword);
        user.setRole(role); user.setStatus(status);
        return user;
    }

    private Merchant mockMerchant(Long id, String username, String encodedPassword, int status, int applyStatus) {
        Merchant m = new Merchant();
        m.setId(id); m.setUsername(username); m.setPassword(encodedPassword);
        m.setStatus(status); m.setApplyStatus(applyStatus);
        return m;
    }
}
