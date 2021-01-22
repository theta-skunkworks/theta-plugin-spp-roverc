/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
#include <M5StickC.h>
#include "BluetoothSerial.h"

TFT_eSprite Disbuff = TFT_eSprite(&M5.Lcd);

BluetoothSerial bts;
#define   BT_SERIAL_BUFF_BYTE      512

void SetChargingCurrent( uint8_t CurrentLevel )
{
  Wire1.beginTransmission(0x34);
  Wire1.write(0x33);
  Wire1.write(0xC0 | ( CurrentLevel & 0x0f));
  Wire1.endTransmission();
}

String serialBtsRead(){
  char  sSerialBuf[BT_SERIAL_BUFF_BYTE];
  String result = "";
  
  if (bts.available()) {
      int iPos=0;
      while (bts.available()) {
        char c = bts.read();
        if (  c == '\n' ) {
          break;
        } else {
          sSerialBuf[iPos] = c;
          iPos++;
          if (iPos==(BT_SERIAL_BUFF_BYTE-1) ) {
            break;
          }
        }
      }
      sSerialBuf[iPos] = 0x00;
      result = String(sSerialBuf);
  }

  return result ;
}

void setup()
{
  M5.begin();
  M5.update();
  
  Wire.begin(0,26,10000);
  M5.Lcd.setRotation(1);
  M5.Lcd.setSwapBytes(false);

  SetChargingCurrent(6);
  
  bts.begin("RoverC");
  
  Serial.begin(115200);
  Serial.print("setup end\n");
}

uint8_t I2CWrite1Byte( uint8_t Addr ,  uint8_t Data )
{
  Wire.beginTransmission(0x38);
  Wire.write(Addr);
  Wire.write(Data);
  return Wire.endTransmission();
}

uint8_t I2CWritebuff( uint8_t Addr,  uint8_t* Data, uint16_t Length )
{
  Wire.beginTransmission(0x38);
  Wire.write(Addr);
  for (int i = 0; i < Length; i++)
  {
    Wire.write(Data[i]);
  }
  return Wire.endTransmission();
}

//==============================
#define MOTOR_SET_MAX_LIMIT   127
#define MOTOR_SET_MIN_LIMIT   -127

#define MOTOR_DRIVE_STOP_VALUE  0
#define MOTOR_DRIVE_BASE_VALUE  40

int8_t forwardRight[4] = { MOTOR_DRIVE_BASE_VALUE, MOTOR_DRIVE_STOP_VALUE,
                           MOTOR_DRIVE_STOP_VALUE, MOTOR_DRIVE_BASE_VALUE };
int8_t forwardLeft[4]  = { MOTOR_DRIVE_STOP_VALUE, MOTOR_DRIVE_BASE_VALUE,
                           MOTOR_DRIVE_BASE_VALUE, MOTOR_DRIVE_STOP_VALUE };
int8_t backRight[4]    = { MOTOR_DRIVE_STOP_VALUE,-MOTOR_DRIVE_BASE_VALUE,
                          -MOTOR_DRIVE_BASE_VALUE, MOTOR_DRIVE_STOP_VALUE };
int8_t backLeft[4]     = {-MOTOR_DRIVE_BASE_VALUE, MOTOR_DRIVE_STOP_VALUE,
                           MOTOR_DRIVE_STOP_VALUE,-MOTOR_DRIVE_BASE_VALUE };

void setAngle2MotorDrive(int AngleDeg, int8_t* outResult)
{
  float weight = 1.0;
  
  if ( 0<=AngleDeg && AngleDeg<45) {
    weight = (float)(45-AngleDeg)/45.0 ;
    //前（右）
    *(outResult+0) = forwardRight[0] + (int8_t)(weight * forwardLeft[0]);
    *(outResult+1) = forwardRight[1] + (int8_t)(weight * forwardLeft[1]);
    *(outResult+2) = forwardRight[2] + (int8_t)(weight * forwardLeft[2]);
    *(outResult+3) = forwardRight[3] + (int8_t)(weight * forwardLeft[3]); 
    
  } else if ( 45<=AngleDeg && AngleDeg<90) {
    weight = (float)(AngleDeg-45)/45.0 ;
    //右(前)
    *(outResult+0) = forwardRight[0] + (int8_t)( weight * backRight[0] );
    *(outResult+1) = forwardRight[1] + (int8_t)( weight * backRight[1] );
    *(outResult+2) = forwardRight[2] + (int8_t)( weight * backRight[2] );
    *(outResult+3) = forwardRight[3] + (int8_t)( weight * backRight[3] ); 
    
  } else if ( 90<=AngleDeg && AngleDeg<135) {
    weight = (float)(135-AngleDeg)/45.0 ;
    //右(後)
    *(outResult+0) = backRight[0] + (int8_t)(weight * forwardRight[0]);
    *(outResult+1) = backRight[1] + (int8_t)(weight * forwardRight[1]);
    *(outResult+2) = backRight[2] + (int8_t)(weight * forwardRight[2]);
    *(outResult+3) = backRight[3] + (int8_t)(weight * forwardRight[3]);
    
  } else if ( 135<=AngleDeg && AngleDeg<=180) {
    weight = (float)(AngleDeg-135)/45.0 ;
    //後（右）
    *(outResult+0) = backRight[0] + (int8_t)(weight * backLeft[0]);
    *(outResult+1) = backRight[1] + (int8_t)(weight * backLeft[1]);
    *(outResult+2) = backRight[2] + (int8_t)(weight * backLeft[2]);
    *(outResult+3) = backRight[3] + (int8_t)(weight * backLeft[3]);

  } else if ( -45<=AngleDeg && AngleDeg<0) {
    weight = (float)(45+AngleDeg)/45.0 ;
    //前（左）
    *(outResult+0) = forwardLeft[0] + (int8_t)(weight * forwardRight[0]);
    *(outResult+1) = forwardLeft[1] + (int8_t)(weight * forwardRight[1]);
    *(outResult+2) = forwardLeft[2] + (int8_t)(weight * forwardRight[2]);
    *(outResult+3) = forwardLeft[3] + (int8_t)(weight * forwardRight[3]);
    
  } else if ( -90<=AngleDeg && AngleDeg<-45) {
    int absAngleDeg = abs(AngleDeg);
    weight = (float)(absAngleDeg-45)/45.0 ;
    //左（前）
    *(outResult+0) = forwardLeft[0] + (int8_t)(weight * backLeft[0]);
    *(outResult+1) = forwardLeft[1] + (int8_t)(weight * backLeft[1]);
    *(outResult+2) = forwardLeft[2] + (int8_t)(weight * backLeft[2]);
    *(outResult+3) = forwardLeft[3] + (int8_t)(weight * backLeft[3]);
    
  } else if ( -135<=AngleDeg && AngleDeg<-90) {
    weight = (float)(135+AngleDeg)/45.0 ;
    //左（後）
    *(outResult+0) = backLeft[0] + (int8_t)(weight * forwardLeft[0]);
    *(outResult+1) = backLeft[1] + (int8_t)(weight * forwardLeft[1]);
    *(outResult+2) = backLeft[2] + (int8_t)(weight * forwardLeft[2]);
    *(outResult+3) = backLeft[3] + (int8_t)(weight * forwardLeft[3]);
    
  } else if ( -180<=AngleDeg && AngleDeg<-135) {
    int absAngleDeg = abs(AngleDeg);
    weight = (float)(absAngleDeg-135)/45.0 ;
    //後（左）
    *(outResult+0) = backLeft[0] + (int8_t)(weight * backRight[0]);
    *(outResult+1) = backLeft[1] + (int8_t)(weight * backRight[1]);
    *(outResult+2) = backLeft[2] + (int8_t)(weight * backRight[2]);
    *(outResult+3) = backLeft[3] + (int8_t)(weight * backRight[3]);
    
  } else {
    *(outResult+0) = MOTOR_DRIVE_STOP_VALUE; *(outResult+1) = MOTOR_DRIVE_STOP_VALUE; 
    *(outResult+2) = MOTOR_DRIVE_STOP_VALUE; *(outResult+3) = MOTOR_DRIVE_STOP_VALUE; 
  }
  
  return ;
}

void setRotateMotorDrive(int setSpeed, int8_t* outResult)
{
    if ( setSpeed > MOTOR_SET_MAX_LIMIT ) {
      setSpeed = MOTOR_SET_MAX_LIMIT;
    }
    if ( setSpeed < MOTOR_SET_MIN_LIMIT ) {
      setSpeed = MOTOR_SET_MIN_LIMIT;
    }
    
    //Right
    *(outResult+1) = (int8_t)(-1*setSpeed);
    *(outResult+3) = (int8_t)(-1*setSpeed);
    
    //Left
    *(outResult+0) = (int8_t)(setSpeed);
    *(outResult+2) = (int8_t)(setSpeed);
  
  return ;
}

//==============================

int pos=0;
int test_deg=0;
void loop()
{
  int directionDeg = 0;
  int motorSpeed = 0;
  int driveTimeMs = 0;
  int8_t  speed_sendbuff[4] = {0,0,0,0};
  int8_t  speed_stop[4] = {0,0,0,0};
  
  String BtsRcvCmd ="";
  BtsRcvCmd = serialBtsRead();
  BtsRcvCmd.trim();
  if ( !(BtsRcvCmd.equals("")) ) {
    Serial.print("rcv=[" + BtsRcvCmd + "]\n");
    
    if ( BtsRcvCmd.startsWith("dir ") ) {
      BtsRcvCmd.replace("dir " , "");
      splitParam2( BtsRcvCmd, &directionDeg, &driveTimeMs);
      Serial.printf("directionDeg=%d, driveTimeMs=%d\n", directionDeg, driveTimeMs);
      setAngle2MotorDrive( directionDeg, speed_sendbuff);
      
      for (int i=0; i<4; i++) {
        Serial.printf("W%d=%d,", i, speed_sendbuff[i]);
      }
      Serial.printf("\n");
      
      I2CWritebuff(0x00,(uint8_t*)speed_sendbuff,4);
      delay(driveTimeMs);
      
    } else if ( BtsRcvCmd.startsWith("rot ") ) {
      BtsRcvCmd.replace("rot " , "");
      splitParam2( BtsRcvCmd, &motorSpeed, &driveTimeMs);
      Serial.printf("motorSpeed=%d, driveTimeMs=%d\n", motorSpeed, driveTimeMs);
      setRotateMotorDrive( motorSpeed, speed_sendbuff);
      
      for (int i=0; i<4; i++) {
        Serial.printf("W%d=%d,", i, speed_sendbuff[i]);
      }
      Serial.printf("\n");
      
      I2CWritebuff(0x00,(uint8_t*)speed_sendbuff,4);
      delay(driveTimeMs);
      
    } else {
      //stop
      I2CWritebuff(0x00,(uint8_t*)speed_stop,4);
      delay(20);
    }

    //受信して動作したことを応答
    bts.print("accept\n");
  }
  
  //stop
  I2CWritebuff(0x00,(uint8_t*)speed_stop,4);
  delay(10);
}

int splitParam2( String inStr, int *param1, int *param2 ) {
  int ret = 0;

  inStr.trim();
  int len = inStr.length();
  int pos = inStr.indexOf(' ', 0);

  if ( (pos > 0) && (len>=3) ){
    String Param1 = inStr.substring(0, pos);
    String Param2 = inStr.substring(pos+1, len);
    //Serial.print("Param1=" + Param1 + ", Param2=" + Param2 +"\n");
    *param1 = Param1.toInt();
    *param2 = Param2.toInt();
  } else {
    ret = -1;
  }
  return ret;
}
