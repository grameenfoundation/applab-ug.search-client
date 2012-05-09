package applab.search.client;

import android.view.Menu;
import applab.client.search.R;

public class GlobalConstants {
    public static final int HOME_ID = Menu.FIRST;
    public static final int INBOX_ID = Menu.FIRST + 1;
    public static final int REFRESH_ID = Menu.FIRST + 2;
    public static final int DELETE_ID = Menu.FIRST + 3;
    public static final int RESET_ID = Menu.FIRST + 4;
    public static final int SETTINGS_ID = Menu.FIRST + 5;
    public static final int EXIT_ID = Menu.FIRST + 6;
    public static final int ABOUT_ID = Menu.FIRST + 7;
    public static final int BACK_ID = Menu.FIRST;
    public static final int CHECK_FOR_UPDATES_ID = Menu.FIRST + 8;

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

    public static final String KEYWORDS_VERSION_KEY = "keywordsVersion";
    public final static String FARMER_CACHE_VERSION_KEY = "lastUpdateDate";

    public static final String FARMER_REG_FORM_HASH = "farmerRegistrationFormHash";

    /** The new tables **/
    public static final String MENU_TABLE_NAME = "menu";
    public static final String MENU_ITEM_TABLE_NAME = "menu_item";
    public static final String AVAILABLE_FARMER_ID_TABLE_NAME = "available_farmer_id";
    public static final String FARMER_LOCAL_CACHE_TABLE_NAME = "farmer_local_cache";

    /** current user name or id */
    public static String intervieweeName;
    
    /** different status of farmer ids **/
    public static final String AVAILABLE_FARMER_ID_UNUSED_STATUS = "0";
    public static final String AVAILABLE_FARMER_ID_USED_STATUS = "1";

    /** current or last known location */
    public static String location = "Unknown";
    
    public static String COUNTRY_CODE = "countryCode";
}
