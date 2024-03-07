package collector.H2;

import collector.DBClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class H2Client extends DBClient {
    public H2Client(String url, String username, String password) {
        super(url, username, password);
    }
}
