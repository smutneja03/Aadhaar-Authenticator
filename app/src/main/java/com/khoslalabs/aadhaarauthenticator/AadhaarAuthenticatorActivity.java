package com.khoslalabs.aadhaarauthenticator;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.aadhaarconnect.bridge.capture.model.auth.AuthCaptureData;
import com.aadhaarconnect.bridge.capture.model.auth.AuthCaptureRequest;
import com.aadhaarconnect.bridge.capture.model.auth.Demographics;
import com.aadhaarconnect.bridge.capture.model.common.Location;
import com.aadhaarconnect.bridge.capture.model.common.LocationType;
import com.aadhaarconnect.bridge.capture.model.common.request.CertificateType;
import com.aadhaarconnect.bridge.capture.model.common.request.Modality;
import com.aadhaarconnect.bridge.capture.model.common.request.ModalityType;
import com.google.gson.Gson;

public class AadhaarAuthenticatorActivity extends AppCompatActivity {
    public static final int QRCODE_REQUEST = 1000;
    public static final int AADHAAR_CONNECT_AUTH_REQUEST = 1001;
    //Enter here the URL of the gateway you will be sending your JSON request to
    private static final String BASE_URL="http://192.168.0.104:8980";

    private EditText aadhaarNumberTextView;
    private EditText aadhaarNameTextView;

    private ImageView qrCodeScanner;

    private RadioGroup radioAuthenticationGroup;
    private RadioButton radioAuthenticationButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aadhaar_authenticator);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        aadhaarNumberTextView = (EditText) findViewById(R.id.aadhaar_number);
        aadhaarNameTextView = (EditText) findViewById(R.id.name);
        qrCodeScanner = (ImageView) findViewById(R.id.barcode);

        addListenerOnButton();

    }


    public void addListenerOnButton() {

        radioAuthenticationGroup = (RadioGroup) findViewById(R.id.radioAuthentication);

        radioAuthenticationButton = (RadioButton) findViewById(R.id.radioDemographic);

        radioAuthenticationGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // checkedId is the RadioButton selected
                radioAuthenticationButton = (RadioButton) findViewById(checkedId);
                if (radioAuthenticationButton.getText().equals("Fingerprint Auth")) {
                    aadhaarNameTextView.setVisibility(View.GONE);
                } else if (radioAuthenticationButton.getText().equals("Demographic Auth")) {
                    aadhaarNameTextView.setVisibility(View.VISIBLE);
                }
                Toast.makeText(AadhaarAuthenticatorActivity.this,
                        radioAuthenticationButton.getText(), Toast.LENGTH_SHORT).show();
            }
        });

    }

    public void scanUsingQRCode(View v) {
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        try {
            startActivityForResult(intent, QRCODE_REQUEST);
        } catch (Exception e) {
            showToast("No QR Code scanning modules found.", Toast.LENGTH_LONG);
        }
    }

    public void authenticate(View v) {
        if (TextUtils.isEmpty(aadhaarNumberTextView.getText())) {
            showToast(
                    "Invalid Aadhaar Number. Please enter a valid Aadhaar Number",
                    Toast.LENGTH_LONG);
            return;
        }

        AuthCaptureRequest authCaptureRequest = new AuthCaptureRequest();
        authCaptureRequest.setAadhaar(aadhaarNumberTextView.getText().toString());
        authCaptureRequest.setCertificateType(CertificateType.preprod);

        if(radioAuthenticationButton.getText().equals("Demographic Auth")) {
            authCaptureRequest.setModality(Modality.demo);

            Demographics demo = new Demographics();
            Demographics.Name name = new Demographics.Name();
            name.setMatchingStrategy(Demographics.MatchingStrategy.exact);
            name.setNameValue(aadhaarNameTextView.getText().toString());

            demo.setName(name);
            authCaptureRequest.setDemographics(demo);
        }
        else if (radioAuthenticationButton.getText().equals("Fingerprint Auth")){
            authCaptureRequest.setModality(Modality.biometric);
            authCaptureRequest.setModalityType(ModalityType.fp);
            authCaptureRequest.setNumOffingersToCapture(2);
        }

        Location loc = new Location();
        loc.setType(LocationType.pincode);
        loc.setPincode("560076");
        authCaptureRequest.setLocation(loc);

        Intent i = new Intent();
        i = new Intent("com.aadhaarconnect.bridge.action.AUTHCAPTURE");
        i.putExtra("REQUEST", new Gson().toJson(authCaptureRequest));
        try {
            startActivityForResult(i, AADHAAR_CONNECT_AUTH_REQUEST);
        } catch (Exception e) {
            Log.e("ERROR", e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QRCODE_REQUEST && resultCode == RESULT_OK
                && data != null) {
            String contents = data.getStringExtra("SCAN_RESULT");
            if (!TextUtils.isEmpty(contents)) {
                String aadhaar = readValue(contents, "uid");
                aadhaarNumberTextView.setText(aadhaar);
                qrCodeScanner.setImageResource(R.drawable.qrcode_green);
            }
            else {
                qrCodeScanner.setImageResource(R.drawable.qrcode_gray);
            }
            return;
        }

        if (resultCode == RESULT_OK && requestCode == AADHAAR_CONNECT_AUTH_REQUEST
                && data != null) {
            String responseStr = data.getStringExtra("RESPONSE");
            final AuthCaptureData authCaptureData = new Gson().fromJson(responseStr, AuthCaptureData.class);
            AadhaarAuthAsyncTask authAsyncTask = new AadhaarAuthAsyncTask(this, authCaptureData);
            authAsyncTask.execute(BASE_URL + "/auth");
            return;
        }
    }

    // HELPER METHODS
    private String readValue(String contents, String dataName) {
        String[] keys;
        if (dataName.contains(",")) {
            keys = dataName.split(",");
        } else {
            keys = new String[] { dataName };
        }
        String value = "";
        for (String key : keys) {
            int startIndex = contents.indexOf(key + "=");
            if (startIndex >= 0) {
                int endIndex = contents.indexOf("\"", startIndex + key.length()
                        + 1 + 1);
                if (endIndex >= 0) {
                    value += " ";
                    value += contents.substring(startIndex + key.length() + 1,
                            endIndex).replaceAll("\"", "");
                }
            }
        }
        return value.trim();
    }

    private void showToast(String text, int duration) {
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }
}