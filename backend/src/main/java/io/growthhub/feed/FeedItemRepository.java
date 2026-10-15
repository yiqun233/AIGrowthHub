package io.growthhub.feed;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface FeedItemRepository extends JpaRepository<FeedItem,UUID>,JpaSpecificationExecutor<FeedItem>{}
