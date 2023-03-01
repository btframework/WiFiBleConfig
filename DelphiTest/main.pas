(*
 * Copyright (C) 2023 Marina Petrichenko
 * 
 * marina@btframework.com  
 *   https://www.facebook.com/marina.petrichenko.1  
 *   https://www.btframework.com
 * 
 * It is free for non-commercial and/or education use only.
 *   
 *)

unit main;

interface

uses
  Forms, StdCtrls, Controls, ComCtrls, wclBluetooth, Classes;

type
  TfmMain = class(TForm)
    wclBluetoothManager: TwclBluetoothManager;
    wclGattClient: TwclGattClient;
    wclBluetoothLeBeaconWatcher: TwclBluetoothLeBeaconWatcher;
    lvDevices: TListView;
    btConnect: TButton;
    btDisconnect: TButton;
    lbLog: TListBox;
    btClear: TButton;
    btReset: TButton;
    btGetStatus: TButton;
    laSsid: TLabel;
    edSsid: TEdit;
    btSetSsid: TButton;
    laPassword: TLabel;
    edPassword: TEdit;
    btSetPassword: TButton;
    btStart: TButton;
    btStop: TButton;
    btUpdate: TButton;
    procedure FormCreate(Sender: TObject);
    procedure FormDestroy(Sender: TObject);
    procedure wclBluetoothLeBeaconWatcherAdvertisementUuidFrame(
      Sender: TObject; const Address, Timestamp: Int64;
      const Rssi: Shortint; const Uuid: TGUID);
    procedure wclBluetoothLeBeaconWatcherAdvertisementFrameInformation(
      Sender: TObject; const Address, Timestamp: Int64;
      const Rssi: Shortint; const Name: String;
      const PacketType: TwclBluetoothLeAdvertisementType;
      const Flags: TwclBluetoothLeAdvertisementFlags);
    procedure btConnectClick(Sender: TObject);
    procedure btClearClick(Sender: TObject);
    procedure wclGattClientConnect(Sender: TObject; const Error: Integer);
    procedure wclGattClientDisconnect(Sender: TObject;
      const Reason: Integer);
    procedure btDisconnectClick(Sender: TObject);
    procedure btResetClick(Sender: TObject);
    procedure btGetStatusClick(Sender: TObject);
    procedure wclGattClientCharacteristicChanged(Sender: TObject;
      const Handle: Word; const Value: TwclGattCharacteristicValue);
    procedure btSetSsidClick(Sender: TObject);
    procedure btSetPasswordClick(Sender: TObject);
    procedure btStartClick(Sender: TObject);
    procedure btStopClick(Sender: TObject);
    procedure btUpdateClick(Sender: TObject);

  private
    FCharacteristic: TwclGattCharacteristic;
    FFound: Boolean;

    procedure ReadAttributes;
  end;

var
  fmMain: TfmMain;

implementation

{$R *.dfm}

uses
  wclErrors, SysUtils, Dialogs, wclConnections, wclBluetoothErrors, Windows;

const
  SERVICE_UUID: TGUID = '{4fafc201-1fb5-459e-8fcc-c5c9c331914b}';
  CHARACTERISTIC_UUID: TGUID = '{beb5483e-36e1-4688-b7f5-ea07361b26a8}';

  WIFI_STATUS_STARTED = $01;
  WIFI_STATUS_STOPPED = $02;
  WIFI_STATUS_NO_CONFIG = $04;
  WIFI_STATUS_ERROR = $05;

  WIFI_CMD_SET_SSID = $01;
  WIFI_CMD_SET_PWD = $02;
  WIFI_CMD_START = $03;
  WIFI_CMD_STOP = $04;
  WIFI_CMD_GET_STATUS = $05;

procedure TfmMain.FormCreate(Sender: TObject);
var
  Res: Integer;
  Radio: TwclBluetoothRadio;
begin
  Res := wclBluetoothManager.Open;
  if Res = WCL_E_SUCCESS then begin
    Res := wclBluetoothManager.GetLeRadio(Radio);
    if Res = WCL_E_SUCCESS then
      wclBluetoothLeBeaconWatcher.Start(Radio);
  end;

  FFound := False;
end;

procedure TfmMain.FormDestroy(Sender: TObject);
begin
  wclGattClient.Disconnect;
  wclBluetoothManager.Close;
end;

procedure TfmMain.wclBluetoothLeBeaconWatcherAdvertisementUuidFrame(
  Sender: TObject; const Address, Timestamp: Int64; const Rssi: Shortint;
  const Uuid: TGUID);
var
  Item: TListItem;
  AddrStr: string;
  i: Integer;
begin
  if CompareMem(@Uuid, @SERVICE_UUID, SizeOf(TGUID)) then begin
    Item := nil;
    AddrStr := IntToHex(Address, 12);

    if lvDevices.Items.Count > 0 then begin
      for i := 0 to lvDevices.Items.Count - 1 do begin
        if lvDevices.Items[i].Caption = AddrStr then begin
          Item := lvDevices.Items[i];
          Break;
        end;
      end;
    end;

    if Item = nil then begin
      Item := lvDevices.Items.Add;
      Item.Caption := AddrStr;
      Item.SubItems.Add('');
    end;
  end;
end;

procedure TfmMain.wclBluetoothLeBeaconWatcherAdvertisementFrameInformation(
  Sender: TObject; const Address, Timestamp: Int64; const Rssi: Shortint;
  const Name: String; const PacketType: TwclBluetoothLeAdvertisementType;
  const Flags: TwclBluetoothLeAdvertisementFlags);
var
  Item: TListItem;
  AddrStr: string;
  i: Integer;
begin
  Item := nil;
  AddrStr := IntToHex(Address, 12);

  if lvDevices.Items.Count > 0 then begin
    for i := 0 to lvDevices.Items.Count - 1 do begin
      if lvDevices.Items[i].Caption = AddrStr then begin
        Item := lvDevices.Items[i];
        Break;
      end;
    end;
  end;

  if Item <> nil then begin
    if Item.SubItems[0] = '' then
      Item.SubItems[0] := Name;
  end;
end;

procedure TfmMain.btConnectClick(Sender: TObject);
var
  Res: Integer;
begin
  if lvDevices.Selected = nil then
    ShowMessage('Select device')

  else begin
    if wclGattClient.State <> csDisconnected then
      ShowMessage('Client connected')

    else begin
      wclGattClient.Address := StrToInt64('$' + lvDevices.Selected.Caption);
      Res := wclGattClient.Connect(wclBluetoothLeBeaconWatcher.Radio);
      if Res <> WCL_E_SUCCESS then
        lbLog.Items.Add('Connect failed: 0x' + IntToHex(Res,8));
    end;
  end;
end;

procedure TfmMain.btClearClick(Sender: TObject);
begin
  lbLog.Clear;
end;

procedure TfmMain.wclGattClientConnect(Sender: TObject;
  const Error: Integer);
begin
  lbLog.Items.Add('Connect: 0x' + IntToHex(Error, 8));
  if Error = WCL_E_SUCCESS then
    ReadAttributes;
end;

procedure TfmMain.wclGattClientDisconnect(Sender: TObject;
  const Reason: Integer);
begin
  lbLog.Items.Add('Disconnected: 0x' + IntToHex(Reason, 8));
  FFound := False;
end;

procedure TfmMain.btDisconnectClick(Sender: TObject);
var
  Res: Integer;
begin
  Res := wclGattClient.Disconnect;
  if Res <> WCL_E_SUCCESS then
    ShowMessage('Disconnect failed: 0x' + IntToHex(Res, 8));
end;

procedure TfmMain.btResetClick(Sender: TObject);
begin
  lvDevices.Items.Clear;
end;

procedure TfmMain.ReadAttributes;
var
  Res: Integer;
  Services: TwclGattServices;
  i: Integer;
  Service: TwclGattService;
  Characteristics: TwclGattCharacteristics;
  Value: TwclGattCharacteristicValue;
begin
  lbLog.Items.Add('Read services');
  Res := wclGattClient.ReadServices(goNone, Services);
  if Res <> WCL_E_SUCCESS then
    lbLog.Items.Add('Read services failed: 0x' + IntToHex(Res, 8))

  else begin
    if Length(Services) = 0 then begin
      Res := WCL_E_BLUETOOTH_LE_ATTRIBUTE_NOT_FOUND;
      lbLog.Items.Add('No services found');
    end;
  end;

  if Res = WCL_E_SUCCESS then begin
    Res := WCL_E_BLUETOOTH_LE_ATTRIBUTE_NOT_FOUND;
    for i := 0 to Length(Services) - 1 do begin
      if CompareMem(@Services[i].Uuid.LongUuid, @SERVICE_UUID, SizeOf(TGUID)) then begin
        Service := Services[i];
        Res := WCL_E_SUCCESS;
        Break;
      end;
    end;

    if Res <> WCL_E_SUCCESS then
      lbLog.Items.Add('Service not found');
  end;

  if Res = WCL_E_SUCCESS then begin
    lbLog.Items.Add('Read characteristics');
    Res := wclGattClient.ReadCharacteristics(Service, goNone, Characteristics);
    if Res <> WCL_E_SUCCESS then
      lbLog.Items.Add('Read characteristics failed: 0x' + IntToHex(Res, 8))

    else begin
      if Length(Characteristics) = 0 then begin
        lbLog.Items.Add('No characteristics found');
        Res := WCL_E_BLUETOOTH_LE_ATTRIBUTE_NOT_FOUND;
      end;
    end;
  end;

  if Res = WCL_E_SUCCESS then begin
    Res := WCL_E_BLUETOOTH_LE_ATTRIBUTE_NOT_FOUND;
    for i := 0 to Length(Services) - 1 do begin
      if CompareMem(@Characteristics[i].Uuid.LongUuid, @CHARACTERISTIC_UUID, SizeOf(TGUID)) then begin
        FCharacteristic := Characteristics[i];
        Res := WCL_E_SUCCESS;
        Break;
      end;
    end;

    if Res <> WCL_E_SUCCESS then
      lbLog.Items.Add('Characteristic not found');
  end;

  if Res = WCL_E_SUCCESS then begin
    lbLog.Items.Add('Subscribing');
    Res := wclGattClient.SubscribeForNotifications(FCharacteristic);
    if Res <> WCL_E_SUCCESS then
      lbLog.Items.Add('Subscribe failed: 0x' + IntToHex(Res, 8));
  end;

  if Res = WCL_E_SUCCESS then begin
    lbLog.Items.Add('All attributes found and set');
    FFound := True;
    Res := wclGattClient.ReadCharacteristicValue(FCharacteristic,
      goReadFromDevice, Value);
    if Res = WCL_E_SUCCESS then begin
      wclGattClientCharacteristicChanged(wclGattClient, FCharacteristic.Handle,
        Value);
    end;
  end else
    wclGattClient.Disconnect;
end;

procedure TfmMain.btGetStatusClick(Sender: TObject);
var
  Res: Integer;
  Value: TwclGattCharacteristicValue;
begin
  if not FFound then
    ShowMessage('Not connected')

  else begin
    Res := wclGattClient.ReadCharacteristicValue(FCharacteristic, goReadFromDevice, Value);
    if Res <> WCL_E_SUCCESS then
      ShowMessage('Read failed: 0x' + IntToHex(Res, 8))

    else begin
      if Length(Value) <> 2 then
        ShowMessage('Inavlid value')

      else begin
        case Value[0] of
          WIFI_STATUS_STARTED:
            ShowMessage('Started: ' + IntToStr(Value[1]));
          WIFI_STATUS_STOPPED:
            ShowMessage('Stopped');
          WIFI_STATUS_NO_CONFIG:
            ShowMessage('Not configured');
          WIFI_STATUS_ERROR:
            ShowMessage('Error');
          else
            ShowMessage('Unknown status');
        end;
      end;
    end;
  end;
end;

procedure TfmMain.wclGattClientCharacteristicChanged(Sender: TObject;
  const Handle: Word; const Value: TwclGattCharacteristicValue);
begin
  if Length(Value) = 2 then begin
    case Value[0] of
      WIFI_STATUS_STARTED:
        lbLog.Items.Add('Started: ' + IntToStr(Value[1]));
      WIFI_STATUS_STOPPED:
        lbLog.Items.Add('Stopped');
      WIFI_STATUS_NO_CONFIG:
        lbLog.Items.Add('Not configured');
      WIFI_STATUS_ERROR:
        lbLog.Items.Add('Error');
      else
        lbLog.Items.Add('Unknown status');
    end;
  end;
end;

procedure TfmMain.btSetSsidClick(Sender: TObject);
var
  Ssid: string;
  Len: Integer;
  Val: TwclGattCharacteristicValue;
  Res: Integer;
begin
  Ssid := edSsid.Text;
  if Ssid = '' then
    ShowMessage('Can not be empty')

  else begin
    Len := Length(Ssid);
    if Len > 16 then
      ShowMessage('Too long')

    else begin
      if not FFound then
        Showmessage('Not connected')

      else begin
        SetLength(Val, Len + 1);
        Val[0] := WIFI_CMD_SET_SSID;
        CopyMemory(@Val[1], Pointer(Ssid), Len);

        Res := wclGattClient.WriteCharacteristicValue(FCharacteristic, Val);
        if Res <> WCL_E_SUCCESS then
          ShowMessage('Write failed: 0x' + IntToHex(Res, 8));
      end;
    end;
  end;
end;

procedure TfmMain.btSetPasswordClick(Sender: TObject);
var
  Pwd: string;
  Len: Integer;
  Val: TwclGattCharacteristicValue;
  Res: Integer;
begin
  Pwd := edPassword.Text;
  if Pwd = '' then
    ShowMessage('Can not be empty')

  else begin
    Len := Length(Pwd);
    if Len > 16 then
      ShowMessage('Too long')

    else begin
      if not FFound then
        Showmessage('Not connected')

      else begin
        SetLength(Val, Len + 1);
        Val[0] := WIFI_CMD_SET_PWD;
        CopyMemory(@Val[1], Pointer(Pwd), Len);

        Res := wclGattClient.WriteCharacteristicValue(FCharacteristic, Val);
        if Res <> WCL_E_SUCCESS then
          ShowMessage('Write failed: 0x' + IntToHex(Res, 8));
      end;
    end;
  end;
end;

procedure TfmMain.btStartClick(Sender: TObject);
var
  Val: TwclGattCharacteristicValue;
  Res: Integer;
begin
  if not FFound then
    Showmessage('Not connected')

  else begin
    SetLength(Val, 1);
    Val[0] := WIFI_CMD_START;

    Res := wclGattClient.WriteCharacteristicValue(FCharacteristic, Val);
    if Res <> WCL_E_SUCCESS then
      ShowMessage('Write failed: 0x' + IntToHex(Res, 8));
  end;
end;

procedure TfmMain.btStopClick(Sender: TObject);
var
  Val: TwclGattCharacteristicValue;
  Res: Integer;
begin
  if not FFound then
    Showmessage('Not connected')

  else begin
    SetLength(Val, 1);
    Val[0] := WIFI_CMD_STOP;

    Res := wclGattClient.WriteCharacteristicValue(FCharacteristic, Val);
    if Res <> WCL_E_SUCCESS then
      ShowMessage('Write failed: 0x' + IntToHex(Res, 8));
  end;
end;

procedure TfmMain.btUpdateClick(Sender: TObject);
var
  Val: TwclGattCharacteristicValue;
  Res: Integer;
begin
  if not FFound then
    Showmessage('Not connected')

  else begin
    SetLength(Val, 1);
    Val[0] := WIFI_CMD_GET_STATUS;

    Res := wclGattClient.WriteCharacteristicValue(FCharacteristic, Val);
    if Res <> WCL_E_SUCCESS then
      ShowMessage('Write failed: 0x' + IntToHex(Res, 8));
  end;
end;

end.
