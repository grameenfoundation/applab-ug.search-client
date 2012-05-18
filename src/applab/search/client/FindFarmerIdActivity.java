package applab.search.client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import applab.client.R;

public class FindFarmerIdActivity extends BaseSearchActivity {

	/** cache where search keywords are stored */
	private Storage farmerLocalCache;
	private Button searchButton;
	private Button useThisIdButton;
	private EditText farmerFirstName;
	private EditText farmerLastName;
	private EditText farmerFatherName;
	private TextView farmerIdResult;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.farmerLocalCache = new Storage(this);
		farmerLocalCache.open();

		setContentView(R.layout.farmer_id);

		// capture our View elements
		this.farmerFirstName = (EditText) findViewById(R.id.firstnametext);
		this.farmerLastName = (EditText) findViewById(R.id.lastnametext);
		this.farmerFatherName = (EditText) findViewById(R.id.fathersnametext);
		this.searchButton = (Button) findViewById(R.id.search);
		this.farmerIdResult = (TextView) findViewById(R.id.farmeridresult);
		this.useThisIdButton = (Button) findViewById(R.id.usethisid);
		useThisIdButton.setVisibility(View.GONE);
	
		
		// add a click listener to the "Search" button
		this.searchButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String farmerId = farmerLocalCache
						.findFarmerIdFromFarmerLocalCacheTable(farmerFirstName
								.getText().toString(), farmerLastName.getText()
								.toString(), farmerFatherName.getText()
								.toString());
				if (farmerId != null){
					farmerIdResult.setText(farmerId);
					useThisIdButton.setVisibility(View.VISIBLE);
					
				}
				
				else {
					
					showToast("Invalid Farmer");
					useThisIdButton.setVisibility(View.GONE);
					searchButton.setVisibility(View.GONE);
					
				}
			}
		});
     
		// add a click listener to the "Use This Id" button
		this.useThisIdButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent newIntent = new Intent(getApplicationContext(),
						MainMenuActivity.class);
				newIntent.putExtra("edit_text", farmerIdResult.getText()
						.toString());
				startActivity(newIntent);
			}
		});
	}
}
