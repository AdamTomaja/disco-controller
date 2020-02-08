#include <Adafruit_NeoPixel.h>
#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

#define PIN       5 
#define NUMPIXELS 8 

const char *ssid =  "";     // replace with your wifi ssid and wpa2 key
const char *pass =  "";
const char* mqtt_server = "192.168.0.66";

Adafruit_NeoPixel pixels(NUMPIXELS, PIN, NEO_GRB + NEO_KHZ800);
WiFiClient espClient;
PubSubClient client(espClient);

StaticJsonDocument<200> doc;

#define MODE_NOOP 0
#define MODE_STROBO 1

unsigned long lastExecution = 0;
int mode = MODE_NOOP;
int interval = 100;

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
uint32_t stroboColor = pixels.Color(200, 200, 200);
void executeStrobo() {
  if((stroboCycle++) % 2 == 0) {
    displayColor(pixels.Color(0, 0, 0));
  } else {
    displayColor(stroboColor);
  }
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
    }
  } else if(cmd == "setInterval") {
    interval = doc["interval"];
  } else if(cmd == "setStroboColor") {
    int r = doc["r"];
    int g = doc["g"];
    int b = doc["b"];
    stroboColor = pixels.Color(r, g, b);
  }
}

void setup() {
  Serial.begin(9600);
  Serial.println("Serial connection initialized");
  pixels.begin();
  Serial.println("Pixels initialized");

  connectToWifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);
  showInitializedAnimation();
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
    }
  }
}