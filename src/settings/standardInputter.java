package settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Allows us to read from Standard In, always using Y/y as a positive response
 */
public class standardInputter extends fimsInputter {
    private static Logger logger = LoggerFactory.getLogger(standardInputter.class);

    public boolean continueOperation(String message) {
        fimsPrinter.out.print(message + "\n\nIf you wish to continue, enter 'Y': ");
        //  open up standard input
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        //  read the response from the command-line; need to use try/catch with the
        try {
            if (br.readLine().equalsIgnoreCase("y")) {
                return true;
            }
        } catch (IOException e) {
            logger.warn("IOException", e);
            return false;
        }
        return false;
    }

    /**
     * haltOperation
     * @param message
     */
    @Override
    public void haltOperation(String message) {
        fimsPrinter.out.print(message);
    }
}
