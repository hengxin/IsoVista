package collector.mysql;

import collector.DBClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MySQLClient extends DBClient {
    public MySQLClient(String url, String username, String password) {
        super(url, username, password);
    }
}
