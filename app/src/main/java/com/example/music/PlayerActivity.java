package com.example.music;

import static com.bumptech.glide.request.RequestOptions.bitmapTransform;

import android.app.Activity;
import android.content.ContentUris;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.music.databinding.ActivityPlayerBinding;
import com.frolo.waveformseekbar.WaveformSeekBar;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import jp.wasabeef.glide.transformations.BlurTransformation;

public class PlayerActivity extends AppCompatActivity {
    private ActivityPlayerBinding binding;
    private ExoPlayer player;
    private Handler handler=new Handler();
    private List<Song> songList = new ArrayList<>();
    private List<Song> shuffledList = new ArrayList<>();
    private int currentIndex=0;
    private  boolean isShuffle=false;
    private boolean isRepeat=false;


    private final Runnable updateRundable=new Runnable() {
        @Override
        public void run() {
            if(player!=null&&player.isPlaying()){
                long currentPosition=player.getCurrentPosition();
                long duration=player.getDuration();
                if(duration>0){
                    float progressPercent=((float) currentPosition/duration);
                    binding.waveformSeekBar.setProgressInPercentage(progressPercent);
                    binding.textElapsed.setText(formatTime((int) (currentPosition/1000)));
                    binding.textDuration.setText(formatTime((int) (duration/1000)));
                }
                handler.postDelayed(this,1000);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding=ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        songList=getIntent().getParcelableArrayListExtra("songList");
        currentIndex=getIntent().getIntExtra("position",0);

        if(songList==null || songList.isEmpty()){
            Toast.makeText(this,"no Songs Found", Toast.LENGTH_SHORT).show();
            finish();
        }
        shuffledList=new ArrayList<>(songList);
        binding.waveformSeekBar.setWaveform(createWaveForm(),true);

        initPlayerWithSong(currentIndex);
        setupControls();

        binding.backBtn.setOnClickListener(v->finish());
    }

    private void setupControls() {

        binding.playpause.setOnClickListener(v->togglePlayPause());
        binding.forward.setOnClickListener(v->playNext());
        binding.previous.setOnClickListener(v->playPrevious());
        binding.shuffle.setOnClickListener(v->toggleShuffle());
        binding.repeat.setOnClickListener(v->toggleRepeat());




        binding.waveformSeekBar.setCallback(new WaveformSeekBar.Callback() {
            @Override
            public void onProgressChanged(WaveformSeekBar seekBar, float percent, boolean fromUser) {
                if(fromUser && player!=null){
                    long duration=player.getDuration();
                    long seekPos=(long) (percent * duration);
                    player.seekTo(seekPos);
                    binding.textElapsed.setText(formatTime((int) (seekPos/1000)));
                }
            }

            @Override
            public void onStartTrackingTouch(WaveformSeekBar seekBar) {
                handler.removeCallbacks(updateRundable);

            }

            @Override
            public void onStopTrackingTouch(WaveformSeekBar seekBar) {
                handler.postDelayed(updateRundable,0);

            }
        });

    }

    private void toggleRepeat() {
        isRepeat=!isRepeat;
        player.setRepeatMode(isRepeat?Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF);
        binding.repeat.setColorFilter(isRepeat?getResources().getColor(R.color.purple)
                : null);
    }

    private void toggleShuffle() {
        isShuffle=!isShuffle;
        if(isShuffle){
            Collections.shuffle(shuffledList);
            binding.shuffle.setColorFilter(getResources().getColor(R.color.purple));
        }else{
            shuffledList=new ArrayList<>(songList);
            binding.shuffle.clearColorFilter();

        }
        initPlayerWithSong(currentIndex);
    }

    private void playPrevious() {
        int listSize=isShuffle?shuffledList.size():songList.size();
        currentIndex=(currentIndex-1+listSize)%listSize;
        initPlayerWithSong(currentIndex);
    }

    private void togglePlayPause() {
        if(player.isPlaying()){
            player.pause();
            handler.removeCallbacks(updateRundable);
        }else {
            player.play();
            handler.postDelayed(updateRundable,0);

        }
        updatePlayPauseButtonIcon();
    }

    private  void initPlayerWithSong(int index){
        Song song=isShuffle?shuffledList.get(index):songList.get(index);

        if(player!=null)player.release();
        player=new ExoPlayer.Builder(this).build();
        player.setRepeatMode(isRepeat? Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updatePlayPauseButtonIcon();
                if(playbackState==Player.STATE_READY){
                    binding.textDuration.setText(formatTime((int)(player.getDuration()/1000)));
                    handler.postDelayed(updateRundable,0);
                } else if(playbackState==Player.STATE_ENDED) {
                    playNext();
                }

            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this, "Error playing media" +error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        player.setMediaItem(MediaItem.fromUri(song.data));
        player.prepare();
        player.play();

        updatePlayPauseButtonIcon();
        updateUI(song);
    }

    private void updateUI(Song song) {
        binding.textTitle.setText(song.title!=null?song.title : "");
        binding.textArtist.setText(song.artist!=null? song.artist : "");
        setTitle(song.title);

        Uri albumArtUri= ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),song.albumId);

        if(hasAlbumArt(albumArtUri)){
            Glide.with(this)
                    .asBitmap()
                    .load(albumArtUri)
                    .circleCrop()
                    .placeholder(R.drawable.ic_music_note_24)
                    .error(R.drawable.ic_music_note_24)
                    .into(binding.imaGEAlbumArtPlayer);

            Glide.with(this)
                    .asBitmap()
                    .load(albumArtUri)
                    .apply(bitmapTransform(new BlurTransformation(25,3)))
                    .circleCrop()
                    .placeholder(R.drawable.ic_music_note_24)
                    .error(R.drawable.ic_music_note_24)
                    .into(binding.bgalbum);
        }else{
            binding.imaGEAlbumArtPlayer.setImageResource((R.drawable.ic_music_note_24));
            binding.bgalbum.setImageResource(R.drawable.ic_music_note_24);
        }
    }

    private boolean hasAlbumArt(Uri albumArtUri){
        try(InputStream inputStream=getContentResolver().openInputStream(albumArtUri)){
            return inputStream!=null;
        }catch(Exception e){
            return false;
        }
    }

    private  String formatTime(int seconds){
        return String.format("%02d:%02d",seconds/60,seconds%60);
    }
    private  int[] createWaveForm(){
        Random random=new Random(System.currentTimeMillis());
        int[] values=new int[50];
        for(int i=0; i<values.length;i++){
            values[i]=5+random.nextInt(50);
        }
        return values;
    }

    private void updatePlayPauseButtonIcon(){
        binding.playpause.setImageResource(
                player!=null && player.isPlaying()?
                        R.drawable.pause_auto_read_pause_24:
                        R.drawable.outline_arrow_right_24
        );
    }
    private  void playNext(){
        currentIndex=(currentIndex+1)%(isShuffle?shuffledList.size():songList.size());
        initPlayerWithSong(currentIndex);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateRundable);
        if(player!=null){
            player.release();
            player=null;
        }
    }
}
