package sam.com.ch10camera5;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    Uri imgUri;           //用來參照拍照存檔的 Uri 物件
    ImageView img_photo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        img_photo = (ImageView) findViewById(R.id.img_photo);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public void onGet(View v) {
        //檢查是否已取得寫入權限，若已取得則回傳PackageManager.PERMISSION_GRANTED
        //呼叫ActivityCompat.requestPermissions()會產生交談窗，向使用者詢問是否允許權限
        //當使用者允許或拒絕後，可透過onRequestPermissionsResult()來接收結果
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 200);
        } else {
            savePhoto();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 200) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                savePhoto();
            } else {
                Toast.makeText(this, "程式需要寫入權限才能運作", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void savePhoto() {
        //在MediaStore新建一個位子(檔案)，並回傳該檔案的URI存放在Uri物件中
        imgUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());

        //呼叫系統的相機拍照
        //將此Uri物件加到Intent的額外資料中，以MediaStore.EXTRA_OUTPUT為名
        //相機程式以此名來讀取未來拍照存檔的URI
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        it.putExtra(MediaStore.EXTRA_OUTPUT, imgUri);
        startActivityForResult(it, 100);
    }

    public void onPick(View v) {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);  //動作設為 "選取內容"
        it.setType("image/*");                              //設定要選取的媒體類型為：所有類型的圖片
        startActivityForResult(it, 101);        //啟動Intent, 並要求傳回選取的圖檔

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case 100:
                    //呼叫廣播通知系統說 MediaStore 有更新
                    Intent it = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, imgUri);
                    sendBroadcast(it);
                    break;

                case 101:
                    //取得選取相片的Uri
                    imgUri = data.getData();
                    break;
            }

            showImg();
        } else {
            Toast.makeText(this, (requestCode == 100 ? "沒拍到照片" : "沒選取照片"), Toast.LENGTH_LONG).show();
        }
    }

    public void showImg() {
        int iw, ih, vw, vh;    //iw,ih為圖片的寬高   vw,wh為ImageView的寬高

        //用BitmapFactory.Options設定載入圖檔的方式
        //ex.只讀取圖檔的寬高資訊,依指定縮小比例載入圖檔
        BitmapFactory.Options option = new BitmapFactory.Options();
        option.inJustDecodeBounds = true;   //設定選項：只讀取圖檔資訊而不載入圖檔

        try {
            //用BitmapFactory讀取圖檔資訊存入 Option 中
            BitmapFactory.decodeStream(getContentResolver().openInputStream(imgUri), null, option);
        } catch (IOException e) {
            Toast.makeText(this, "讀取照片資訊時發生錯誤", Toast.LENGTH_LONG).show();
            Log.e("TAG", "error occurs.", e);
            return;
        }

        //再由 option 中讀出圖檔寬度與高度
        iw = option.outWidth;
        ih = option.outHeight;

        //取得 ImageView 的寬度與高度
        vw = img_photo.getWidth();
        vh = img_photo.getHeight();


        Boolean isRotate;   //用來儲存是否需要旋轉

        int scale;
        if (iw < ih) {                           //如果圖片的寬度小於高度
            isRotate = false;                    //不需要旋轉
            scale = Math.min(iw / vw, ih / vh);  // 計算縮小比率
        } else {
            isRotate = true;                      //需要旋轉
            scale = Math.min(ih / vw, iw / vh);   // 將 ImageView 的寬、高互換來計算縮小比率
        }

        option.inJustDecodeBounds = false;       //關閉只載入圖檔資訊的選項
        option.inSampleSize = scale;             //設定縮小比例, 例如 2 則長寬都將縮小為原來的 1/2

        //用BitmapFactory讀取圖檔並指派給Bitmap物件
        Bitmap bmp = null;
        try {
            bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(imgUri), null, option);
        } catch (IOException e) {
            Toast.makeText(this, "URI可能有誤", Toast.LENGTH_LONG).show();
            Log.e("TAG", "error occurs.", e);
        }

        if (isRotate) {
            Matrix matrix = new Matrix();      //建立 Matrix 矩陣物件
            matrix.postRotate(90);     //設定順時針旋轉角度

            //用Bitmap.createBitmap()將原來的 Bitmap 產生一個新的 Bitmap時
            //可以將 Matrix 矩陣物件 帶入來旋轉圖片
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        }

        img_photo.setImageBitmap(bmp);

        new AlertDialog.Builder(this)
                .setTitle("圖檔資訊")
                .setMessage("圖檔URI:" + imgUri.toString() +
                        "\n原始尺寸:" + iw + "X" + ih +
                        "\n載入尺寸:" + bmp.getWidth() + "X" + bmp.getHeight() +
                        "\n顯示尺寸:" + vw + "X" + vh + (isRotate ? "旋轉" : ""))
                .setNeutralButton("關閉", null)
                .show();

    }

    public void onShare(View v) {

        Intent it = new Intent(Intent.ACTION_SEND);
        it.setType("images/*");
        it.putExtra(Intent.EXTRA_STREAM, imgUri);
        startActivity(it);

    }
}
