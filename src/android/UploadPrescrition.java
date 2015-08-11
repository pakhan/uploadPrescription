package com.medlife.uploadprescription;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.PrivateKeySignature;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class UploadPrescrition extends AppCompatActivity implements View.OnClickListener, KeyChainAliasCallback {

    private Button uploadPrescription;
    private ImageView prescription;
    private static final int CAMERA_REQUEST = 1000;
    private static final int START_SELECT_CERTIFICATE = 1;
    private static final int GET_CERTI_CRED = 4;
    private Context mContext;
    private String TAG = UploadPrescrition.class.getSimpleName();
   /* public void setMailContent(String mMailContent) {
        String mMailContent1;
        mMailContent1 = mMailContent;
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_prescrition);


        uploadPrescription = (Button)findViewById(R.id.upload_prescription);
        uploadPrescription.setOnClickListener(this);
        mContext = UploadPrescrition.this;
        prescription = (ImageView)findViewById(R.id.prescription);
        if(Helper.getInstance().getAlias(getApplicationContext())==null) {
            chooseCert();
        }else{
            captureImage();
        }
    }

    @Override
    public void onClick(View view) {

        switch (view.getId())
        {
            case R.id.upload_prescription:
                    captureImage();
                break;
            default:
                break;
        }

    }
    @Override
    public void alias(String alias) {
        if (alias != null) {

            Message msg = new Message();
            msg.what = START_SELECT_CERTIFICATE;
            msg.obj = alias;
            mHandler.sendMessage(msg);
        }
        else{
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Toast.makeText(UploadPrescrition.this, "Please select certificate", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
     Handler mHandler = new Handler(){

        @Override
        public void handleMessage(android.os.Message msg) {
            //PublicKey key = null;
            switch (msg.what) {
                case START_SELECT_CERTIFICATE:
                    Helper.getInstance().setAlias(mContext, msg.obj.toString());

                case GET_CERTI_CRED:
                    new ExtractCertiInfoAsync(mContext, mHandler).execute();
                    break;

                case Constants.SIGN_PDF:
                    //setMailContent(Helper.getInstance().getStringPref(getApplicationContext(),ExtractCertiInfoAsync.PUBLIC_KEY));
                    signPdf((PrivateKey) msg.getData().get(Constants.KEY_PK), (X509Certificate[]) msg.getData().get(Constants.KEY_CHAIN));
                    break;

                default:
                    break;
            }
        }
    };

    private void chooseCert() {
        KeyChain.choosePrivateKeyAlias(this, this, new String[]{}, null, "medlife.com", -1, Helper.NEW_ALIAS);
    }

    private void captureImage()
    {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode == RESULT_OK && requestCode == CAMERA_REQUEST)
        {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            prescription.setImageBitmap(photo);

            Uri tempUri = getImageUri(getApplicationContext(), photo);
            File finalFile = new File(getRealPathFromURI(tempUri));
            try {
                Document document=new Document();
                PdfWriter.getInstance(document, new FileOutputStream(Environment.getExternalStorageDirectory()+"/prescription.pdf"));
                document.open();
                Image image = Image.getInstance(finalFile.getAbsolutePath());
                document.add(new Paragraph("Signed PDF"));
                document.add(image);
                document.close();
                mHandler.sendEmptyMessage(GET_CERTI_CRED);
            } catch (DocumentException | IOException e) {
                Log.e(TAG, e.getMessage());
            }


        }
    }

    private void signPdf(PrivateKey pk, X509Certificate[] chain){
        FileOutputStream os = null;
        try {
            String path = Environment.getExternalStorageDirectory()+"/signed.pdf";
            os = new FileOutputStream(path);
            PdfReader reader = new PdfReader(Environment.getExternalStorageDirectory()+"/prescription.pdf");
            PdfStamper stamper = PdfStamper.createSignature(reader, os, '\0');
            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            ExternalSignature es = new PrivateKeySignature(pk, "SHA256", null);
            ExternalDigest digest = new BouncyCastleDigest();
            MakeSignature.signDetached(appearance, digest, es, chain, null, null, null, 0, MakeSignature.CryptoStandard.CMS);

            new SendEmailAsync(UploadPrescrition.this).execute();

        } catch (GeneralSecurityException | DocumentException | IOException e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if(os!=null){
                try {
                    os.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }

    public String getRealPathFromURI(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        String filePath = null;
        try {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            filePath = cursor.getString(idx);
        }catch(Exception e){
            Log.e(TAG, e.getMessage());
            return null;
        }
        finally {
            if(cursor!=null){
                cursor.close();
            }
        }
        return filePath;
    }
}
