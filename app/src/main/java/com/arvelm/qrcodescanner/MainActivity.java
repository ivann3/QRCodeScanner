package com.arvelm.qrcodescanner;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import com.google.zxing.Result;
import me.dm7.barcodescanner.zxing.ZXingScannerView;
import android.os.IBinder;
import android.widget.Toast;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public class MainActivity extends AppCompatActivity implements ZXingScannerView.ResultHandler{

    /**
     * The Custom NvRam must be define in the SourceCode(alps)
     * Details Information refer to the MTK Document---(Customization in NvRAM .pdf)
     */
    private static final String NVRAMPATH = "/data/nvram/APCFG/APRDEB/Custom_Battery";

    private ZXingScannerView mZXingScannerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    @Override
    protected void onPause() {
        super.onPause();
    }


    @Override
    public void handleResult(Result result) {
        if ( result.getText().length() >0 || !(result.getText().equals("")) ){
            String message = result.getText().trim();
            showAlertDialog(message);
        }
    }

    private void showAlertDialog(final String message) {
        AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
        adBuilder.setTitle("ScanValue");
        adBuilder.setMessage(message.trim());
        adBuilder.setCancelable(false);
        adBuilder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            if (isShowWarningAlertDialog(message)){
                return;
            }
                nvRAMWrite(message);
            }
        });
        adBuilder.setNegativeButton("Rescan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                resumeScanView();
            }
        });
        AlertDialog alertDialog = adBuilder.create();
        alertDialog.show();
    }


    private boolean isShowWarningAlertDialog(String message) {
        if ( message.length() >2 ){
            AlertDialog.Builder builder= new AlertDialog.Builder(this);
            builder.setTitle("Warning");
            builder.setMessage(" Scan Value Must Be Two char ");
            builder.setCancelable(true);
            builder.setNegativeButton("Rescan", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    resumeScanView();
                }
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            Toast.makeText(getApplicationContext()," Save Failed ", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }


    public void onScan(View view){
        grantPermission();
        startScanView();
    }


    public void onRead(View view) {
        readData();
    }


    private void grantPermission(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED){

            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},1);
        }
    }


    private void startScanView() {
        mZXingScannerView = new ZXingScannerView(getApplicationContext());
        setContentView(mZXingScannerView);
        mZXingScannerView.setResultHandler(this);
        mZXingScannerView.startCamera();
    }


    private void resumeScanView() {
        mZXingScannerView.resumeCameraPreview(MainActivity.this);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Log.d("PERMISSION_GRANTED-----","Accept");
                } else {
                    Log.d("PERMISSION_GRANTED-----","Deny");
                }
        }
    }


    /**
     * write the string to the NvRAM
     * @param string
     */
    private void nvRAMWrite(String string) {
        IBinder iBinder = getNvRAMAgentIBinder();
        NvRAMAgent agent = NvRAMAgent.Stub.asInterface(iBinder);

        if (agent != null) {
//            Toast.makeText(this, "agent Succeed: " + agent.getClass(), Toast.LENGTH_SHORT).show();
            try {
                Log.d("NvRAMPath-----", NVRAMPATH);
//                int tag = agent.writeFileByName(NVRAMPATH, getBytes(0));
                int tag = agent.writeFileByName(NVRAMPATH, getBytes(string));
                if (tag > 0) {
                    Toast.makeText(this, "NvRAM Write Succeed: \n" + NVRAMPATH, Toast.LENGTH_LONG).show();
                    resumeScanView();
                } else {
                    Toast.makeText(this, "NvRAM Write Failed", Toast.LENGTH_LONG).show();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "agent Failed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Read the data from the NvRAM
     */
    public void readData() {
        IBinder binder = getNvRAMAgentIBinder();
        NvRAMAgent agent = NvRAMAgent.Stub.asInterface(binder);

        byte[] bytes = null;
        try {
            bytes = agent.readFileByName(NVRAMPATH);// read buffer from NvRam
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
//        int b = byte2int(buff);
        char[] info = getChars(bytes);
        Toast.makeText(this, "Custom_Battery Data: " + String.valueOf(info), Toast.LENGTH_LONG).show();
    }


    /**
     * getBytes(String data)--- convert the string value to byte[]
     * @param data
     * @return
     */
    private byte[] getBytes(String data)
    {
        Charset charset = Charset.forName("UTF-8");
        byte[] bytes = data.getBytes(charset);
        for(byte b : bytes){
            Log.d("GETBYTES_STRING-----", String.valueOf(b));
        }
        return bytes;
    }

    private char[] getChars (byte[] bytes) {
        Charset cs = Charset.forName ("UTF-8");
        ByteBuffer bb = ByteBuffer.allocate (bytes.length);
        bb.put (bytes);
        bb.flip ();
        CharBuffer cb = cs.decode (bb);
        return cb.array();
    }


    /**
     * 1.use the Reflection (Class.forName) to catch the " ServiceManager "
     * 2.get the " getService "
     * 3.invoke the Specical " NvRAMAgent ", casting it to the IBinder type
     * @return
     */
    private IBinder getNvRAMAgentIBinder() {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("android.os.ServiceManager");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        Method method_getService = null;
        try {
            method_getService = clazz.getMethod("getService", String.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        IBinder iBinder = null;
        try {
            iBinder = (IBinder) method_getService.invoke(null, "NvRAMAgent");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        if (iBinder != null) {
            Log.d("NvRAMAgent----- ","NvRAMAgentIBinder Exist");
            return iBinder;
        } else {
            Log.d("NvRAMAgent----- ","NvRAMAgentIBinder Not Exist");
            return null;
        }
    }


    /**
     * getBytes(int data)--- convert the Integer value to byte[]
     */
    @SuppressWarnings("unused")
    private byte[] getBytes(int data){
        byte[] bytes = new byte[4];
        bytes[0] = (byte)   (data & 0xff);
        bytes[1] = (byte) ( (data & 0xff00) >> 8 );
        bytes[2] = (byte) ( (data & 0xff0000) >> 16);
        bytes[3] = (byte) ( (data & 0xff000000) >> 24);
        return bytes;
    }

    /**
     * byte2int(byte[] res)--- convert the byte[] to Integer value
     */
    @SuppressWarnings("unused")
    public int byte2int(byte[] res) {
        int targets = (res[0] & 0xff) | ((res[1] << 8) & 0xff00)
                | ((res[2] << 24) >>> 8) | (res[3] << 24);
        return targets;
    }


    @SuppressWarnings("unused")
    private byte[] getBytes (char[] chars) {
        Charset cs = Charset.forName ("UTF-8");
        CharBuffer cb = CharBuffer.allocate (chars.length);
        cb.put (chars);
        cb.flip ();
        ByteBuffer bb = cs.encode (cb);

        return bb.array();
    }
}
