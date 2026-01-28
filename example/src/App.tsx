import * as React from 'react';

import {
  StyleSheet,
  TouchableOpacity,
  Text,
  SafeAreaView,
  Dimensions,
  Image,
  View,
  Alert,
} from 'react-native';
import {
  RNMediapipe,
  switchCamera,
  capturePhoto,
} from '@thinksys/react-native-mediapipe';

const { width: deviceWidth, height: deviceHeight } = Dimensions.get('window');

export default function App() {
  const { width, height } = Dimensions.get('window');
  const [photoUri, setPhotoUri] = React.useState<string | null>(null);

  const onFlip = () => {
    switchCamera();
  };

  const handleLandmark = (data: any) => {
    console.log('Body Landmark Data:', data);
  };

  const handleCapture = async () => {
    try {
      const result = await capturePhoto();
      console.log('Captured photo:', result);
      setPhotoUri(result.uri);
      Alert.alert('Success', `Photo saved to: ${result.path}`);
    } catch (error: any) {
      console.error('Capture failed:', error);
      Alert.alert('Error', error.message || 'Failed to capture photo');
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <RNMediapipe
        style={styles.tsMediapipeView}
        width={width}
        height={height}
        onLandmark={handleLandmark}
        face={true}
        leftArm={true}
        rightArm={true}
        leftWrist={true}
        rightWrist={true}
        torso={true}
        leftLeg={true}
        rightLeg={true}
        leftAnkle={true}
        rightAnkle={true}
        frameLimit={25} // ios only(set the frame rate during initialization)
      />
      <View style={styles.buttonContainer}>
        <TouchableOpacity onPress={onFlip} style={styles.btnView}>
          <Text style={styles.btnTxt}>Switch Camera</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={handleCapture} style={styles.captureBtn}>
          <Text style={styles.btnTxt}>Capture Photo</Text>
        </TouchableOpacity>
      </View>
      {photoUri && (
        <View style={styles.previewContainer}>
          <Image source={{ uri: photoUri }} style={styles.preview} />
          <TouchableOpacity
            onPress={() => setPhotoUri(null)}
            style={styles.closeBtn}
          >
            <Text style={styles.btnTxt}>Close</Text>
          </TouchableOpacity>
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: 'white',
    flex: 1,
  },
  buttonContainer: {
    position: 'absolute',
    bottom: 42,
    flexDirection: 'row',
    justifyContent: 'center',
    alignSelf: 'center',
    gap: 10,
  },
  btnView: {
    width: 150,
    height: 60,
    backgroundColor: 'green',
    padding: 8,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  captureBtn: {
    width: 150,
    height: 60,
    backgroundColor: 'blue',
    padding: 8,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnTxt: { color: 'white' },
  tsMediapipeView: {
    alignSelf: 'center',
  },
  previewContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.9)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  preview: {
    width: deviceWidth * 0.9,
    height: deviceHeight * 0.7,
    resizeMode: 'contain',
  },
  closeBtn: {
    marginTop: 20,
    backgroundColor: 'red',
    padding: 12,
    borderRadius: 8,
    minWidth: 100,
    alignItems: 'center',
  },
});
