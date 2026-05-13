package com.code.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JWT 工具类。
 *
 * <p>负责签发、解析与校验 JWT。当前采用 HS256 对称签名算法，适合单体后端或受控微服务场景；如果未来需要跨系统
 * 分发公钥校验，则通常会演进为非对称算法。</p>
 */
@Component
public class JwtUtil {

    /**
     * JWT 签名密钥。
     *
     * <p>HS256 属于对称加密签名，签发与验签都依赖同一把密钥，因此正式环境必须妥善保管，不能使用默认值。</p>
     */
    @Value("${jwt.secret:changeit123456789012345678901234}")
    private String jwtSecret;

    /**
     * token 有效期，单位毫秒。
     *
     * <p>有效期越长，用户体验越平滑，但 token 泄露后的风险窗口也越长，因此它本质上是在安全性与易用性之间做平衡。</p>
     */
    @Value("${jwt.expiration-ms:3600000}")
    private long jwtExpirationMs;

    /**
     * 由配置中的密钥字符串构造签名 Key。
     *
     * <p>这里对密钥长度有隐含要求，因此默认值只是便于本地启动的占位符，正式环境必须替换为安全密钥。</p>
     */
    private Key key() {
        // JJWT 的 hmacShaKeyFor 会根据字节数组生成符合 HMAC 算法要求的 Key 对象。
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * 生成只包含用户名 subject 的 JWT。
     *
     * <p>由于没有把角色等信息塞进 token，服务端后续仍需查库恢复权限，但能确保角色变更后无需等待旧 token 自然失效。</p>
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + jwtExpirationMs);

        // JWT 常见标准字段：
        // subject   -> 当前登录主体，这里存邮箱/用户名；
        // issuedAt  -> token 签发时间；
        // expiration-> 过期时间，用于服务端校验失效窗口。
        return Jwts.builder().setSubject(username).setIssuedAt(now).setExpiration(exp).signWith(key(), SignatureAlgorithm.HS256).compact();
    }

    /**
     * 解析 token 中的用户名。
     */
    public String getUsernameFromToken(String token) {
        // parseClaimsJws 会同时完成签名校验与 Claims 解析，校验失败会直接抛异常。
        return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody().getSubject();
    }

    /**
     * 验证 token 是否可被接受。
     *
     * <p>包含签名错误、过期、格式异常在内的所有 JJWT 相关异常都会被吞掉并返回 false，交由调用方按未认证处理。</p>
     */
    public boolean validateJwtToken(String token) {
        try {
            // 这里只要能成功解析，就说明 token 的签名、格式、过期时间都满足要求。
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {

            // 不向外暴露具体异常细节，是一种保守安全策略：
            // 调用方只需要知道“该 token 不可信”，无需区分是过期、篡改还是格式错误。
            return false;
        }
    }
}

