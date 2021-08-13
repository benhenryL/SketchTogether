package edu.skku.sketchdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Button;
import android.widget.ImageView;
import android.os.Bundle;
import android.view.View;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import edu.skku.sketchdemo.ml.EffiExtractor;
import smile.TSNE;

public class MainActivity extends AppCompatActivity {
    private final int GET_IMAGE_FOR_GALLERYVIEW = 201;
    private ImageView galleryImage;
    private ImageView suggestImage1;
    private ImageView suggestImage2;
    private ImageView suggestImage3;
    private ImageView suggestImage4;
    private Button suggestButton;
    private Bitmap galleryImageBmp;
    private Classifier classifier;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        galleryImage = findViewById(R.id.galleryImageView);
        suggestImage1 = findViewById(R.id.suggestedImageView1);
        suggestImage2 = findViewById(R.id.suggestedImageView2);
        suggestImage3 = findViewById(R.id.suggestedImageView3);
        suggestImage4 = findViewById(R.id.suggestedImageView4);
        suggestButton = findViewById(R.id.suggestButton);
        suggestButton.setVisibility(View.INVISIBLE);

        classifier = new Classifier();
        loadData();

        galleryImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                startActivityForResult(intent, GET_IMAGE_FOR_GALLERYVIEW);
            }
        });

        suggestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Drawable galleryImageDrawable = galleryImage.getDrawable();
                galleryImageBmp = ((BitmapDrawable)galleryImageDrawable).getBitmap();
                Bitmap galleryImageBmpResized = Bitmap.createScaledBitmap(galleryImageBmp, 224, 224, false);
                List<String> suggestedImageList = new ArrayList<String>();

                try {
                    EffiExtractor model = EffiExtractor.newInstance(MainActivity.this);
                    TensorImage image = TensorImage.fromBitmap(galleryImageBmpResized);
                    EffiExtractor.Outputs outputs = model.process(image);
                    TensorBuffer galleryImageTensorBuffer = outputs.getFeatureAsTensorBuffer();
                    model.close();

                    // change data type for tsne
                    float[] floatGalleryImageFeatures = galleryImageTensorBuffer.getFloatArray();
                    double[] galleryImageFeatures = new double[1280];
                    for (int i = 0; i < 1280; i++) {
                        galleryImageFeatures[i] = floatGalleryImageFeatures[i];
                    }
                    int featureNum = classifier.getFeatureNum();
                    classifier.setFeatureSetRow(galleryImageFeatures, featureNum);

                    TSNE tsne = new TSNE(classifier.getFeatureSet(), 3);
                    for (int i = 0; i < featureNum + 1; i++) {
                        double x = tsne.coordinates[i][0];
                        double y = tsne.coordinates[i][1];
                        double z = tsne.coordinates[i][2];
                        classifier.setDataPointListElement(i, x, y);
                    }

                    DataPoint lastDataPoint = classifier.dataPointList.get(featureNum); // get last one (!= gallery image......)
                    suggestedImageList = classifier.classify(lastDataPoint);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Resources resources = MainActivity.this.getResources();
                int resourceId1 = resources.getIdentifier(suggestedImageList.get(1), "drawable", MainActivity.this.getPackageName());
                int resourceId2 = resources.getIdentifier(suggestedImageList.get(2), "drawable", MainActivity.this.getPackageName());
                int resourceId3 = resources.getIdentifier(suggestedImageList.get(3), "drawable", MainActivity.this.getPackageName());
                int resourceId4 = resources.getIdentifier(suggestedImageList.get(4), "drawable", MainActivity.this.getPackageName());

                suggestImage1.setImageResource(resourceId1);
                suggestImage2.setImageResource(resourceId2);
                suggestImage3.setImageResource(resourceId3);
                suggestImage4.setImageResource(resourceId4);

            }
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Uri selectedImageUri;
        RequestOptions option1 = new RequestOptions().centerInside();

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            Glide.with(getApplicationContext()).asBitmap().load(selectedImageUri).into(new SimpleTarget<Bitmap>() {
                @Override
                public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                    galleryImage.setImageBitmap(resource);
                }
            });
            suggestButton.setVisibility(View.VISIBLE);
            suggestImage1.setImageResource(0);
            suggestImage2.setImageResource(0);
            suggestImage3.setImageResource(0);
            suggestImage4.setImageResource(0);
        }
    }

    public void loadData() {
        try {
            String dataSetFile = "features.csv";
            int idx = 0;
            int featureLen = classifier.getFeatureLen();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getAssets().open(dataSetFile)));
            String line;
            while ((line = bufferedReader.readLine()) != null){
                String[] data = line.split(",");
                double value;
                for (int i = 0; i < featureLen; i++) {
                    value = Double.parseDouble(data[i]);
                    classifier.setFeatureSetElement(value, idx, i);
                }
                idx++;
            }

            String fileNameFile = "filename.txt";
            BufferedReader fileNameReader = new BufferedReader(new InputStreamReader(getAssets().open(fileNameFile)));

            String filename;
            DataPoint dataPoint;
            int i = 0;
            while ((line = fileNameReader.readLine()) != null){
                filename = line;
                dataPoint = new DataPoint(0, 0, 0, filename);
                classifier.setDataPointListElement(dataPoint);
            }
            dataPoint = new DataPoint(0, 0, 0, "galleryImage");
            classifier.setDataPointListElement(dataPoint);
        } catch (Exception exception){
            exception.printStackTrace();
        }
    }
}