/**
 * Copyright (C) 2010 Grameen Foundation
Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
 */

package applab.search.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import applab.client.AboutDialog;
import applab.client.search.R;

public class LocationActivity extends Activity implements LocationListener {
    private LocationManager lm;
    private TextView mTextView;
    private Button mButton, nextButton;
    private LinearLayout button_layout;
    private ProgressDialog mLocationDialog;
    private String alt, lon, lat, accuracy, intervieweeName, location;
    private boolean next = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.capture);
        Bundle extras = this.getIntent().getExtras();
        if (extras != null) {
            intervieweeName = extras.getString("name");
            location = extras.getString("location");
        }
        mButton = (Button)findViewById(R.id.loc_button);
        mTextView = (TextView)findViewById(R.id.location);
        /**
         * XXX Display the location for this session if available
         */
        if (GlobalConstants.location != null
                && !GlobalConstants.location.contentEquals("Unknown")) {
            mTextView.setText(GlobalConstants.location.replace(",", "\n"));
            mButton.setText(getString(R.string.get_gps2));
            next = true;
        }

        nextButton = (Button)findViewById(R.id.next_button);
        button_layout = (LinearLayout)findViewById(R.id.button_layout);
        lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if (!next) {
            mButton.setText(getString(R.string.get_gps));
            // nextButton.setEnabled(false);
            button_layout.setVisibility(View.INVISIBLE);
        }
        setupLocationDialog();

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // nextButton.setText("Next");
                mLocationDialog.show();
                update();

            }

        });
        nextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                /*
                 * Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.loading),
                 * Toast.LENGTH_LONG); toast.setGravity(Gravity.CENTER, 0, 0); toast.show();
                 */
                Intent i = new Intent(getApplicationContext(),
                        SearchActivity.class);
                i.putExtra("name", intervieweeName);
                if (accuracy != null) {
                    // i.putExtra("location", location);
                    GlobalConstants.location = "Latitude = " + lat + ", Longitude = "
                            + lon + ", Altitude = " + alt + "m"
                            + ", Accuracy = " + accuracy + "m";
                }
                else {
                    i.putExtra("location", "Unknown");
                    GlobalConstants.location = "Unknown";
                }
                startActivity(i);
                finish();

            }

        });
    }

    public void update() {
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    public void cancel() {
        lm.removeUpdates(this);
    }

    public void getLastKnownLocation() {
        Location arg0 = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        String lat = formatGps(arg0.getLatitude(), "lat");
        String lon = formatGps(arg0.getLongitude(), "lon");
        String accuracy = String.valueOf(arg0.getAccuracy());
        String alt = String.valueOf(arg0.getAltitude());
        mTextView.setText("Latitude = " + lat + "\nLongitude = " + lon
                + "\nAltitude = " + alt + "m" + "\nAccuracy = " + accuracy
                + "m");

    }

    @Override
    public void onLocationChanged(Location arg0) {
        lat = formatGps(arg0.getLatitude(), "lat");
        lon = formatGps(arg0.getLongitude(), "lon");
        accuracy = String.valueOf(arg0.getAccuracy());
        alt = String.valueOf(arg0.getAltitude());
        mLocationDialog.setMessage("Accuracy = " + accuracy + "m");
    }

    public void onProviderDisabled(String arg0) {
        // mStatus.setText("Provider disabled "+ arg0);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
                .setMessage(
                        "GPS is disabled on this phone. \nPlease enable it.")
                .setCancelable(false).setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
        AlertDialog alert = builder.create();
        mLocationDialog.dismiss();
        alert.show();
    }

    public void onProviderEnabled(String arg0) {
        // mStatus.setText("Provider enabled "+ arg0);
    }

    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        // mStatus.setText("Status changed to " + arg0 + " [" + arg1 + "]");
    }

    private String formatGps(double coordinates, String type) {
        String location = Double.toString(coordinates);
        String degreeSign = "\u00B0";
        String degree = location.substring(0, location.indexOf("."))
                + degreeSign;
        location = "0." + location.substring(location.indexOf(".") + 1);
        double temp = Double.valueOf(location) * 60;
        location = Double.toString(temp);
        String mins = location.substring(0, location.indexOf(".")) + "'";

        location = "0." + location.substring(location.indexOf(".") + 1);
        temp = Double.valueOf(location) * 60;
        location = Double.toString(temp);
        String secs = location.substring(0, location.indexOf(".")) + '"';
        if (type.equalsIgnoreCase("lon")) {
            if (degree.startsWith("-")) {
                degree = "W " + degree.replace("-", "") + mins + secs;
            }
            else
                degree = "E " + degree.replace("-", "") + mins + secs;
        }
        else {
            if (degree.startsWith("-")) {
                degree = "S " + degree.replace("-", "") + mins + secs;
            }
            else
                degree = "N " + degree.replace("-", "") + mins + secs;
        }
        return degree;
    }

    private void setupLocationDialog() {
        // dialog displayed while fetching gps location
        mLocationDialog = new ProgressDialog(this);
        DialogInterface.OnClickListener geopointButtonListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON1:
                    if (accuracy != null) {
                        // skip = false;
                        mTextView.setText("Latitude = " + lat
                                + "\nLongitude = " + lon + "\nAltitude = "
                                + alt + "m" + "\nAccuracy = " + accuracy + "m");

                    }
                    else {
                        mTextView
                                .setText("Latitude = Unknown\nLongitude = Unknown\nAltitude = Unknown\nAccuracy = Unknown");

                    }
                    cancel();
                    if (accuracy == null) {
                        nextButton.setText("Skip");
                        mButton.setText(getString(R.string.get_gps));
                    }
                    else {
                        nextButton.setText("Next");
                        mButton.setText(getString(R.string.get_gps2));
                    }
                    // nextButton.setEnabled(true);
                    button_layout.setVisibility(View.VISIBLE);
                    dialog.dismiss();
                    break;
                case DialogInterface.BUTTON2:
                    cancel();
                    // skip = true;
                    // nextButton.setEnabled(true);
                    button_layout.setVisibility(View.VISIBLE);
                    if (accuracy == null)
                        nextButton.setText("Skip");
                    dialog.dismiss();
                    break;
            }
            // on cancel, stop gps
        }
        };

        // back button doesn't cancel
        mLocationDialog.setCancelable(false);
        mLocationDialog.setIndeterminate(true);
        mLocationDialog.setTitle(getString(R.string.gps_header));
        mLocationDialog.setMessage(getString(R.string.gps_message));
        mLocationDialog.setButton(DialogInterface.BUTTON1, "OK",
                geopointButtonListener);
        mLocationDialog.setButton(DialogInterface.BUTTON2, "Cancel",
                geopointButtonListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        boolean result = super.onCreateOptionsMenu(menu);

        menu.add(0, GlobalConstants.INBOX_ID, 0, getString(R.string.menu_inbox));
        /*
         * menu.add(0, globalVariables.REFRESH_ID, 0, getString(R.string.menu_refresh));
         */
        menu.add(0, GlobalConstants.ABOUT_ID, 1, getString(R.string.menu_about));
        menu.add(0, GlobalConstants.EXIT_ID, 0, getString(R.string.menu_exit));
        menu.add(0, GlobalConstants.HOME_ID, 0, getString(R.string.menu_home));

        return result;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        switch (item.getItemId()) {

            case GlobalConstants.INBOX_ID:
                Intent j = new Intent(getApplicationContext(),
                        InboxListActivity.class);
                j.putExtra("name", intervieweeName);
                j.putExtra("location", location);
                startActivity(j);
                finish();
                return true;
                /*
                 * case globalVariables.REFRESH_ID: mStorage.open(); Connect(); return true;
                 */
            case GlobalConstants.ABOUT_ID:
                AboutDialog.show(this, getString(R.string.app_version), getString(R.string.app_name),
                        getString(R.string.release_date), getString(R.string.info), R.drawable.icon);
                return true;
            case GlobalConstants.HOME_ID:
                Intent l = new Intent(getApplicationContext(),
                        MainMenuActivity.class);
                l.putExtra("name", intervieweeName);
                l.putExtra("location", location);
                startActivity(l);
                finish();
                return true;
            case GlobalConstants.EXIT_ID:
                this.finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.cancel();
    }
}