package io.growthhub.common;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
@Component
public class BusinessClock {
 private final JdbcTemplate jdbc;private final String fallback;
 public BusinessClock(JdbcTemplate jdbc,@Value("${app.timezone}") String fallback){this.jdbc=jdbc;this.fallback=fallback;}
 public ZoneId zone(){String configured=jdbc.queryForObject("select timezone from app_setting where id=1",String.class);return ZoneId.of(configured==null||configured.isBlank()?fallback:configured);}
 public LocalDate today(){return LocalDate.now(zone());}
}
