package io.growthhub.feed;

import io.growthhub.common.ApiException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps supported creator URLs to a fixed RSS, RSSHub, or scraper adapter. */
@Component
public class FeedPlatformRegistry {
    public record Platform(String id, String label, String note) {}
    public record Source(String platformId, String adapterType, String label, String profileUrl, String fetchUrl) {}
    private record Adapter(String pattern, String type, String route, String fetchHost) {}

    private static final List<Platform> PLATFORMS = List.of(
            new Platform("xueqiu", "雪球", "公开用户动态；平台限流或登录可见内容可能无法读取。"),
            new Platform("jisilu", "集思录", "读取公开主页；需要登录的内容暂不采集。"),
            new Platform("taoguba", "淘股吧", "用户博客主页。"),
            new Platform("eastmoney", "东方财富", "股吧用户主页。"),
            new Platform("ths", "同花顺", "个人动态主页。"),
            new Platform("jiuquaner", "韭圈儿", "用户主页；页面结构变更可能影响抓取。"),
            new Platform("youzhiyouxing", "有知有行", "用户主页；页面结构变更可能影响抓取。"),
            new Platform("wechat", "微信公众号", "可填公众号名称或包含公众号标识的文章链接；RSSHub 实例须支持该路由。"),
            new Platform("zhihu", "知乎", "支持用户或专栏主页；部分内容会受登录和反爬限制。"),
            new Platform("weibo", "微博", "公开用户动态；部分用户内容可能需要登录态。"),
            new Platform("xiaohongshu", "小红书", "公开用户笔记；部分路由依赖 RSSHub 浏览器运行环境。"),
            new Platform("juejin", "掘金", "公开用户文章。"),
            new Platform("csdn", "CSDN", "直接读取 CSDN 用户 RSS。"),
            new Platform("v2ex", "V2EX", "直接读取 V2EX 用户 RSS。")
    );

    private static final Map<String, Adapter> ADAPTERS = Map.ofEntries(
            Map.entry("xueqiu", new Adapter("https?://xueqiu\\.com/(?:u/)?(\\d+)", "rsshub", "/xueqiu/user/%s", "")),
            Map.entry("jisilu", new Adapter("https?://(?:www\\.)?jisilu\\.cn/people/([^/]+)", "scraper", "https://www.jisilu.cn/people/%s", "www.jisilu.cn")),
            Map.entry("taoguba", new Adapter("https?://(?:www\\.)?taoguba\\.com\\.cn/blog/(\\d+)", "scraper", "https://www.taoguba.com.cn/blog/%s", "www.taoguba.com.cn")),
            Map.entry("eastmoney", new Adapter("https?://guba\\.eastmoney\\.com/people/(\\w+)", "rsshub", "/eastmoney/user/%s", "")),
            Map.entry("ths", new Adapter("https?://t\\.10jqka\\.com\\.cn/u/(\\w+)", "scraper", "https://t.10jqka.com.cn/u/%s", "t.10jqka.com.cn")),
            Map.entry("jiuquaner", new Adapter("https?://(?:www\\.)?jiuquaner\\.com/user/(\\w+)", "scraper", "https://www.jiuquaner.com/user/%s", "www.jiuquaner.com")),
            Map.entry("youzhiyouxing", new Adapter("https?://(?:www\\.)?youzhiyouxing\\.cn/people/(\\w+)", "scraper", "https://youzhiyouxing.cn/people/%s", "youzhiyouxing.cn")),
            Map.entry("wechat", new Adapter("https?://mp\\.weixin\\.qq\\.com/(?:s\\?__biz=|cgi-bin/).*", "rsshub", "/wechat/mp/%s", "")),
            Map.entry("zhihu", new Adapter("https?://(?:www\\.)?zhihu\\.com/(?:people|column)/([^/?#]+)", "rsshub", "", "")),
            Map.entry("weibo", new Adapter("https?://(?:www\\.)?weibo\\.com/u/(\\d+)", "rsshub", "/weibo/user/%s", "")),
            Map.entry("xiaohongshu", new Adapter("https?://(?:www\\.)?xiaohongshu\\.com/user/profile/(\\w+)", "rsshub", "/xiaohongshu/user/%s", "")),
            Map.entry("juejin", new Adapter("https?://(?:www\\.)?juejin\\.cn/user/(\\d+)", "rsshub", "/juejin/posts/%s", "")),
            Map.entry("csdn", new Adapter("https?://blog\\.csdn\\.net/([^/]+)", "rss", "https://blog.csdn.net/%s/rss/list", "blog.csdn.net")),
            Map.entry("v2ex", new Adapter("https?://(?:www\\.)?v2ex\\.com/member/([^/]+)", "rss", "https://www.v2ex.com/feed/member/%s.xml", "www.v2ex.com"))
    );

    private final String rssHubBase;

    public FeedPlatformRegistry(Environment environment) {
        this.rssHubBase = environment.getProperty("RSSHUB_URL", "https://rsshub.app").replaceAll("/+$", "");
        URI base = URI.create(rssHubBase);
        if (base.getHost() == null || base.getUserInfo() != null || !("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))) {
            throw new IllegalStateException("RSSHUB_URL 必须是有效的 HTTP(S) 服务地址");
        }
    }

    public List<Platform> platforms() { return PLATFORMS; }

    public Source createSource(String platformId, String label, String rawProfileUrl) {
        Platform platform = PLATFORMS.stream().filter(p -> p.id().equals(platformId)).findFirst()
                .orElseThrow(() -> new ApiException(400, "FEED_PLATFORM_INVALID", "请选择支持的平台"));
        Adapter adapter = ADAPTERS.get(platformId);
        String raw = rawProfileUrl.strip();
        if ("wechat".equals(platformId) && !raw.contains("://")) {
            if (!raw.matches("[\\p{L}\\p{N}_.-]{2,100}")) throw new ApiException(400, "FEED_PROFILE_URL_INVALID", "请输入公众号名称或包含 __biz 的文章链接");
            String name = label == null || label.isBlank() ? platform.label() + " · " + raw : label.strip();
            if (name.length() > 200) throw new ApiException(400, "FEED_LABEL_INVALID", "订阅名称最多 200 个字符");
            return new Source(platformId, adapter.type(), name, "wxid:" + raw, rssHubBase + String.format(adapter.route(), encode(raw)));
        }
        URI profile;
        try { profile = URI.create(raw); }
        catch (Exception e) { throw new ApiException(400, "FEED_PROFILE_URL_INVALID", "请输入有效的主页链接"); }
        if (profile.getHost() == null || profile.getUserInfo() != null || !("https".equalsIgnoreCase(profile.getScheme()) || "http".equalsIgnoreCase(profile.getScheme())) || profile.toString().length() > 2000) {
            throw new ApiException(400, "FEED_PROFILE_URL_INVALID", "请输入有效的 HTTP(S) 主页链接");
        }
        String matchUrl = "wechat".equals(platformId) ? profile.toASCIIString() : profile.getScheme() + "://" + profile.getRawAuthority() + profile.getRawPath();
        Matcher matcher = Pattern.compile(adapter.pattern(), Pattern.CASE_INSENSITIVE).matcher(matchUrl);
        if (!matcher.matches()) throw new ApiException(400, "FEED_PROFILE_URL_INVALID", "主页链接格式与所选平台不匹配");
        String uid;
        if ("wechat".equals(platformId)) {
            Matcher biz = Pattern.compile("(?:\\?|&)__biz=([\\w=]+)").matcher(profile.toString());
            if (!biz.find()) throw new ApiException(400, "FEED_PROFILE_URL_INVALID", "请粘贴包含公众号标识（__biz）的文章链接");
            uid = biz.group(1);
        } else uid = matcher.group(1);

        String fetchUrl = switch (platformId) {
            case "zhihu" -> rssHubBase + (profile.getPath().contains("/column/") ? "/zhihu/zhuanlan/" : "/zhihu/posts/") + encode(uid);
            default -> adapter.route().startsWith("/") ? rssHubBase + String.format(adapter.route(), encode(uid)) : String.format(adapter.route(), encode(uid));
        };
        String name = label == null || label.isBlank() ? platform.label() + " · " + uid : label.strip();
        if (name.length() > 200) throw new ApiException(400, "FEED_LABEL_INVALID", "订阅名称最多 200 个字符");
        String home = "wechat".equals(platformId) ? profile.toASCIIString() : profile.toASCIIString();
        return new Source(platformId, adapter.type(), name, home, fetchUrl);
    }

    public boolean allowedFetchHost(String platformId, URI uri) {
        Adapter adapter = ADAPTERS.get(platformId);
        if (adapter == null) return false;
        if ("rsshub".equals(adapter.type())) return uri.getHost() != null && uri.getHost().equalsIgnoreCase(URI.create(rssHubBase).getHost());
        return uri.getHost() != null && uri.getHost().equalsIgnoreCase(adapter.fetchHost());
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
}
