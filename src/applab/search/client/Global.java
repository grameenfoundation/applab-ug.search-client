package applab.search.client;

import android.view.Menu;

public class Global {
	public static final int HOME_ID = Menu.FIRST;
	public static final int INBOX_ID = Menu.FIRST + 1;
	public static final int REFRESH_ID = Menu.FIRST + 2;
	public static final int DELETE_ID = Menu.FIRST + 3;
	public static final int RESET_ID = Menu.FIRST + 4;
	public static final int SETTINGS_ID = Menu.FIRST + 5;
	public static final int EXIT_ID = Menu.FIRST + 6;
	public static final int ABOUT_ID = Menu.FIRST + 7;
	public static final int BACK_ID = Menu.FIRST;

    public static final int KEYWORD_DOWNLOAD_STARTING = 0;
    public static final int CONNECTION_ERROR = 1;
    public static final int KEYWORD_DOWNLOAD_SUCCESS = 2;
    public static final int KEYWORD_DOWNLOAD_FAILURE = 3;
    public static final int KEYWORD_PARSE_SUCCESS = 4;
    public static final int KEYWORD_PARSE_ERROR = 5;
    public static final int DISMISS_WAIT_DIALOG = 6;
    public static final int KEYWORD_PARSE_GOT_NODE_TOTAL = 7;

	public static final int UPDATE_DIALOG = 0;
	public static final int CONNECT_DIALOG = 1;
	public static final int PARSE_DIALOG = 2;
	public static final int SETUP_DIALOG = 3;

	/** keywords table 1 */
	public static final String DATABASE_TABLE = "keywords";

	/** keywords table 2 */
	public static final String DATABASE_TABLE2 = "keywords2";

	/** current user name or id */
	public static String intervieweeName;

	/** current or last known location */
	public static String location = "Unknown";
}
