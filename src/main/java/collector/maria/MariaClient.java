package collector.maria;

import collector.DBClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MariaClient extends DBClient {
    public MariaClient(String url, String username, String password) {
        super(url, username, password);
    }
}
