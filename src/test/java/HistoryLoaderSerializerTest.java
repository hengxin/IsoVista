import history.History;
import history.loader.TextHistoryLoader;
import history.serializer.TextHistorySerializer;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HistoryLoaderSerializerTest {
    @Test
    public void testHistoryLoaderAndSerializer() {
        String currentDirectory = System.getProperty("user.dir") + "/src/test/";
        String tempFilePath = currentDirectory + "temp_test_history.txt";

        TextHistoryLoader historyLoader = new TextHistoryLoader();
        History<Long, Long> history = historyLoader.loadHistory(currentDirectory + "sample_history.txt");

        TextHistorySerializer historySerializer = new TextHistorySerializer();
        historySerializer.serializeHistory(history, tempFilePath);

        History<Long, Long> loadedHistory = historyLoader.loadHistory(tempFilePath);

        assertEquals(history, loadedHistory);

         File tempFile = new File(tempFilePath);
         if (tempFile.delete()) {
            System.out.println("Successfully deleted the temporary file");
         } else {
            System.out.println("Failed to delete the temporary file");
         }
    }
}
