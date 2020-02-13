#include <Adafruit_NeoPixel.h>
#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "arduinoFFT.h"

#define PIN       5 
#define NUMPIXELS 8 

const char *ssid =  "UPC7F439B2";     // replace with your wifi ssid and wpa2 key
const char *pass =  "";
const char* mqtt_server = "192.168.0.66";

Adafruit_NeoPixel pixels(NUMPIXELS, PIN, NEO_GRB + NEO_KHZ800);
WiFiClient espClient;
PubSubClient client(espClient);
arduinoFFT FFT = arduinoFFT();

StaticJsonDocument<200> doc;

#define MODE_NOOP 0
#define MODE_STROBO 1
#define MODE_STATIC 2
#define TRANSITION 3
#define MODE_MIC_FFT 4

unsigned long lastExecution = 0;
int mode = MODE_MIC_FFT;
int interval = 0;

/* FFT */
#define SAMPLES 256              //Must be a power of 2
#define SAMPLING_FREQUENCY 10000 //Hz, must be 10000 or less due to ADC conversion time. Determines maximum frequency that can be analysed by the FFT.
unsigned int sampling_period_us;
unsigned long microseconds;
byte peak[] = {0,0,0,0,0,0,0};
double vReal[SAMPLES];
double vImag[SAMPLES];
unsigned long newTime, oldTime;
int micFftFilter = 200;
int micFftAmp = 50;
int micFftFreqOffset = 0;
/* FFT END */

void connectToWifi() {
  Serial.println("Connecting to ");
  Serial.println(ssid); 

  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, pass); 
  while (WiFi.status() != WL_CONNECTED) 
  {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi connected"); 
  Serial.print("Local ip: ");
  Serial.println(WiFi.localIP());
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Create a random client ID
    String clientId = "ESP8266Client-";
    clientId += String(random(0xffff), HEX);
    // Attempt to connect
    if (client.connect(clientId.c_str())) {
      Serial.println("connected");
      // Once connected, publish an announcement...
      client.publish("outTopic", "hello world");
      // ... and resubscribe
      client.subscribe("inTopic");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}


void displayColor(uint32_t color) {
  for(int i=0; i<NUMPIXELS; i++) {
      pixels.setPixelColor(i, color);
  }
  pixels.show(); 
}

void showInitializedAnimation() {
  for(int i = 0; i < 5; i++) {
    displayColor(pixels.Color(255, 0, 0));
    delay(200);
    displayColor(pixels.Color(0, 0, 0));
    delay(200);
  }
}

int stroboCycle = 0;
uint32_t colorA = pixels.Color(200, 200, 200);
uint32_t colorB = pixels.Color(0, 0, 0);

void executeStrobo() {
  if((stroboCycle++) % 2 == 0) {
    displayColor(colorA);
  } else {
    displayColor(colorB);
  }
}

void executeStatic() {
  displayColor(colorA);
}

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");
  for (int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }
  Serial.println();

  DeserializationError error = deserializeJson(doc, payload);
  if (error) {
    Serial.print(F("deserializeJson() failed: "));
    Serial.println(error.c_str());
    return;
  }

  const char* command = doc["cmd"];
  String cmd = String(command);
  if(cmd == "displayColor") {
    Serial.println("Setting color!");
    int r = doc["r"];
    int g = doc["g"];
    int b = doc["b"];
    displayColor(pixels.Color(r, g, b));
  } else if(cmd == "setMode") {
    String md = doc["mode"];
    if(md == "strobo") {
      mode = MODE_STROBO;
    } else if(md == "noop") {
      mode = MODE_NOOP;
    } else if(md == "static") {
      mode = MODE_STATIC;
    } else if(md == "mic_fft") {
      mode = MODE_MIC_FFT;
    }
  } else if(cmd == "setInterval") {
    interval = doc["interval"];
  } else if(cmd == "setColor") {
    int r = doc["r"];
    int g = doc["g"];
    int b = doc["b"];
    String bank = doc["bank"];
    if(bank == "A") {
      colorA = pixels.Color(r, g, b);
    } else if(bank == "B") {
      colorB = pixels.Color(r, g, b);
    }
  } else if (cmd == "setMicFft") {
    micFftFilter = doc["micFftFilter"];
    micFftAmp = doc["micFftAmp"];
    micFftFreqOffset = doc["micFftFreqOffset"];
  }
}

void setup() {
  sampling_period_us = round(1000000 * (1.0 / SAMPLING_FREQUENCY));

  Serial.begin(9600);
  Serial.println("Serial connection initialized");
  pixels.begin();
  Serial.println("Pixels initialized");

  connectToWifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);
  showInitializedAnimation();
}

void executeSound() {
   for (int i = 0; i < SAMPLES; i++) {
    newTime = micros()-oldTime;
    oldTime = newTime;
    vReal[i] = analogRead(A0); // A conversion takes about 1mS on an ESP8266
    vImag[i] = 0;
    while (micros() < (newTime + sampling_period_us)) { /* do nothing to wait */ }
  }
  FFT.Windowing(vReal, SAMPLES, FFT_WIN_TYP_HAMMING, FFT_FORWARD);
  FFT.Compute(vReal, vImag, SAMPLES, FFT_FORWARD);
  FFT.ComplexToMagnitude(vReal, vImag, SAMPLES);
 
  
  int r = 0, g = 0, b = 0;

   for (int i = 2; i < (SAMPLES/2); i++){ // Don't use sample 0 and only first SAMPLES/2 are usable. Each array eleement represents a frequency and its value the amplitude.
    double value = vReal[i];
    int intValue = (int)value;
    
    if (value > micFftFilter) { // Add a crude noise filter, 4 x amplitude or more
      // if (i<=5 )             r = intValue; // 125Hz
      if (i > (5 + micFftFreqOffset)   && i<= (12 + micFftFreqOffset))  r = intValue; // 250Hz
      if (i > (12 + micFftFreqOffset)  && i<= (32 + micFftFreqOffset))  g = intValue; // 500Hz
      if (i > (32 + micFftFreqOffset)  && i<= (62 + micFftFreqOffset))  b = intValue; // 1000H
    }
  }

  double amp = 100.0 / micFftAmp;
  displayColor(pixels.Color(r * amp, g * amp, b * amp));
}

void loop() {
    if (!client.connected()) {
    reconnect();
  }

  client.loop();

  unsigned long time = millis();
  if(time - lastExecution > interval) {
    lastExecution = time;

    switch(mode) {
      case MODE_STROBO:
        executeStrobo();
      break;
      case MODE_STATIC:
        executeStatic();
      break;
      case MODE_MIC_FFT:
        executeSound();
      break;
    }
  }
}
