package io.growthhub.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class SecurityConfigurationTest {
 @Test void noRemoteListenerWithoutAuthentication(){assertThrows(IllegalStateException.class,()->new SecurityConfiguration(new MockEnvironment().withProperty("server.address","0.0.0.0").withProperty("AUTH_ENABLED","false")));}
 @Test void enabledLoginRequiresStrongCredentials(){for(String password:java.util.List.of("short","replace-with-private-login-password")){var config=new SecurityConfiguration(new MockEnvironment().withProperty("AUTH_ENABLED","true").withProperty("AUTH_PASSWORD",password));assertThrows(IllegalStateException.class,config::users);}}
 @Test void passwordIsStoredAsBcryptNotPlaintext(){var config=new SecurityConfiguration(new MockEnvironment().withProperty("AUTH_ENABLED","true").withProperty("AUTH_PASSWORD","unit-test-long-password"));assertTrue(config.users().loadUserByUsername("growthhub").getPassword().startsWith("{bcrypt}$2"));}
 @Test void throttlesRepeatedLoginAttempts()throws Exception{var filter=new SecurityConfiguration.LoginThrottle();var request=new org.springframework.mock.web.MockHttpServletRequest("POST","/api/v1/auth/login");request.setServletPath("/api/v1/auth/login");for(int attempt=0;attempt<15;attempt++){var response=new org.springframework.mock.web.MockHttpServletResponse();filter.doFilterInternal(request,response,(q,r)->{});assertEquals(200,response.getStatus());}var blocked=new org.springframework.mock.web.MockHttpServletResponse();filter.doFilterInternal(request,blocked,(q,r)->fail("must not reach authentication"));assertEquals(429,blocked.getStatus());assertEquals("60",blocked.getHeader("Retry-After"));assertTrue(blocked.getContentAsString().contains("requestId"));assertTrue(blocked.getContentAsString().contains("fieldErrors"));}
}
