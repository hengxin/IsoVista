package collector.postgresql;

import collector.DBClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostgreSQLClient extends DBClient {
    public PostgreSQLClient(String url, String username, String password) {
        super(url, username, password);
    }
}
