object fmMain: TfmMain
  Left = 399
  Top = 230
  BorderStyle = bsSingle
  Caption = 'ESP32 WiFi Configurator'
  ClientHeight = 385
  ClientWidth = 466
  Color = clBtnFace
  Font.Charset = DEFAULT_CHARSET
  Font.Color = clWindowText
  Font.Height = -11
  Font.Name = 'MS Sans Serif'
  Font.Style = []
  OldCreateOrder = False
  Position = poScreenCenter
  OnCreate = FormCreate
  OnDestroy = FormDestroy
  PixelsPerInch = 96
  TextHeight = 13
  object laSsid: TLabel
    Left = 8
    Top = 120
    Width = 25
    Height = 13
    Caption = 'SSID'
  end
  object laPassword: TLabel
    Left = 8
    Top = 152
    Width = 46
    Height = 13
    Caption = 'Password'
  end
  object lvDevices: TListView
    Left = 8
    Top = 8
    Width = 369
    Height = 97
    Columns = <
      item
        Caption = 'Address'
        Width = 120
      end
      item
        Caption = 'Name'
        Width = 200
      end>
    GridLines = True
    HideSelection = False
    ReadOnly = True
    RowSelect = True
    TabOrder = 0
    ViewStyle = vsReport
  end
  object btConnect: TButton
    Left = 384
    Top = 8
    Width = 75
    Height = 25
    Caption = 'Connect'
    TabOrder = 1
    OnClick = btConnectClick
  end
  object btDisconnect: TButton
    Left = 384
    Top = 40
    Width = 75
    Height = 25
    Caption = 'Disconnect'
    TabOrder = 2
    OnClick = btDisconnectClick
  end
  object lbLog: TListBox
    Left = 8
    Top = 208
    Width = 449
    Height = 169
    ItemHeight = 13
    TabOrder = 3
  end
  object btClear: TButton
    Left = 384
    Top = 176
    Width = 75
    Height = 25
    Caption = 'Clear'
    TabOrder = 4
    OnClick = btClearClick
  end
  object btReset: TButton
    Left = 384
    Top = 80
    Width = 75
    Height = 25
    Caption = 'Reset'
    TabOrder = 5
    OnClick = btResetClick
  end
  object btGetStatus: TButton
    Left = 384
    Top = 112
    Width = 75
    Height = 25
    Caption = 'Get status'
    TabOrder = 6
    OnClick = btGetStatusClick
  end
  object edSsid: TEdit
    Left = 64
    Top = 112
    Width = 121
    Height = 21
    TabOrder = 7
    Text = 'WiFiGatt'
  end
  object btSetSsid: TButton
    Left = 200
    Top = 112
    Width = 75
    Height = 25
    Caption = 'Set'
    TabOrder = 8
    OnClick = btSetSsidClick
  end
  object edPassword: TEdit
    Left = 64
    Top = 144
    Width = 121
    Height = 21
    TabOrder = 9
    Text = '12345678'
  end
  object btSetPassword: TButton
    Left = 200
    Top = 144
    Width = 75
    Height = 25
    Caption = 'Set'
    TabOrder = 10
    OnClick = btSetPasswordClick
  end
  object btStart: TButton
    Left = 288
    Top = 112
    Width = 75
    Height = 25
    Caption = 'Start'
    TabOrder = 11
    OnClick = btStartClick
  end
  object btStop: TButton
    Left = 288
    Top = 144
    Width = 75
    Height = 25
    Caption = 'Stop'
    TabOrder = 12
    OnClick = btStopClick
  end
  object btUpdate: TButton
    Left = 384
    Top = 144
    Width = 75
    Height = 25
    Caption = 'Update'
    TabOrder = 13
    OnClick = btUpdateClick
  end
  object wclBluetoothManager: TwclBluetoothManager
    Left = 288
    Top = 232
  end
  object wclGattClient: TwclGattClient
    OnCharacteristicChanged = wclGattClientCharacteristicChanged
    OnConnect = wclGattClientConnect
    OnDisconnect = wclGattClientDisconnect
    Left = 280
    Top = 176
  end
  object wclBluetoothLeBeaconWatcher: TwclBluetoothLeBeaconWatcher
    OnAdvertisementFrameInformation = wclBluetoothLeBeaconWatcherAdvertisementFrameInformation
    OnAdvertisementUuidFrame = wclBluetoothLeBeaconWatcherAdvertisementUuidFrame
    Left = 168
    Top = 200
  end
end
