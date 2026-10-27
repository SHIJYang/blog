#include "file_drop_target.h"

#include <shellapi.h>

#include <cstdio>
#include <string>

namespace {

// Must match the channel used by the desktop_drop plugin: the Dart side
// listens on this channel.
constexpr char kChannelName[] = "desktop_drop";

std::string WideToUtf8(const std::wstring& value) {
  if (value.empty()) {
    return std::string();
  }
  const int size = ::WideCharToMultiByte(CP_UTF8, 0, value.c_str(),
                                         static_cast<int>(value.size()),
                                         nullptr, 0, nullptr, nullptr);
  if (size <= 0) {
    return std::string();
  }
  std::string converted(size, 0);
  ::WideCharToMultiByte(CP_UTF8, 0, value.c_str(),
                        static_cast<int>(value.size()), converted.data(), size,
                        nullptr, nullptr);
  return converted;
}

}  // namespace

FileDropTarget::FileDropTarget(flutter::BinaryMessenger* messenger) {
  channel_ = std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
      messenger, kChannelName, &flutter::StandardMethodCodec::GetInstance());
}

FileDropTarget::~FileDropTarget() = default;

bool FileDropTarget::Register(HWND window) {
  if (window == nullptr) {
    return false;
  }
  const HRESULT result = ::RegisterDragDrop(window, this);
  if (FAILED(result)) {
    // DRAGDROP_E_ALREADYREGISTERED means the plugin already owns this window.
    return false;
  }
  window_ = window;
  return true;
}

void FileDropTarget::Unregister() {
  if (window_ != nullptr) {
    ::RevokeDragDrop(window_);
    window_ = nullptr;
  }
}

HRESULT FileDropTarget::QueryInterface(REFIID riid, void** object) {
  if (object == nullptr) {
    return E_POINTER;
  }
  if (riid == IID_IUnknown || riid == IID_IDropTarget) {
    *object = static_cast<IDropTarget*>(this);
    AddRef();
    return S_OK;
  }
  *object = nullptr;
  return E_NOINTERFACE;
}

ULONG FileDropTarget::AddRef() {
  return static_cast<ULONG>(::InterlockedIncrement(&ref_count_));
}

ULONG FileDropTarget::Release() {
  const LONG count = ::InterlockedDecrement(&ref_count_);
  return static_cast<ULONG>(count < 0 ? 0 : count);
}

bool FileDropTarget::HasFiles(IDataObject* data) {
  if (data == nullptr) {
    return false;
  }
  FORMATETC format = {CF_HDROP, nullptr, DVASPECT_CONTENT, -1, TYMED_HGLOBAL};
  return data->QueryGetData(&format) == S_OK;
}

void FileDropTarget::SendLocation(const char* method, POINTL point) {
  if (channel_ == nullptr || window_ == nullptr) {
    return;
  }
  POINT client = {point.x, point.y};
  ::ScreenToClient(window_, &client);
  // Client coordinates in physical pixels; Dart converts them with the device
  // pixel ratio, exactly like the plugin does.
  flutter::EncodableList arguments = {
      flutter::EncodableValue(static_cast<double>(client.x)),
      flutter::EncodableValue(static_cast<double>(client.y))};
  channel_->InvokeMethod(method,
                         std::make_unique<flutter::EncodableValue>(arguments));
}

HRESULT FileDropTarget::DragEnter(IDataObject* data,
                                  DWORD key_state,
                                  POINTL point,
                                  DWORD* effect) {
  const bool accepted = HasFiles(data);
  if (effect != nullptr) {
    *effect = accepted ? DROPEFFECT_COPY : DROPEFFECT_NONE;
  }
  if (accepted) {
    SendLocation("entered", point);
  }
  return S_OK;
}

HRESULT FileDropTarget::DragOver(DWORD key_state,
                                 POINTL point,
                                 DWORD* effect) {
  if (effect != nullptr) {
    *effect = DROPEFFECT_COPY;
  }
  SendLocation("updated", point);
  return S_OK;
}

HRESULT FileDropTarget::DragLeave() {
  if (channel_ != nullptr) {
    channel_->InvokeMethod("exited", nullptr);
  }
  return S_OK;
}

void FileDropTarget::AddFilePaths(IDataObject* data,
                                  flutter::EncodableList* files) {
  FORMATETC format = {CF_HDROP, nullptr, DVASPECT_CONTENT, -1, TYMED_HGLOBAL};
  STGMEDIUM medium = {};
  if (data->GetData(&format, &medium) != S_OK) {
    return;
  }

  auto* drop = static_cast<HDROP>(::GlobalLock(medium.hGlobal));
  if (drop != nullptr) {
    const UINT count = ::DragQueryFileW(drop, 0xFFFFFFFF, nullptr, 0);
    for (UINT index = 0; index < count; ++index) {
      // Query the required length first so long paths are not truncated.
      const UINT length = ::DragQueryFileW(drop, index, nullptr, 0);
      if (length == 0) {
        continue;
      }
      std::wstring buffer(length + 1, L'\0');
      const UINT copied = ::DragQueryFileW(
          drop, index, buffer.data(), static_cast<UINT>(buffer.size()));
      if (copied == 0) {
        continue;
      }
      buffer.resize(copied);
      files->push_back(flutter::EncodableValue(WideToUtf8(buffer)));
    }
    ::GlobalUnlock(medium.hGlobal);
  }
  ::ReleaseStgMedium(&medium);
}

HRESULT FileDropTarget::Drop(IDataObject* data,
                             DWORD key_state,
                             POINTL point,
                             DWORD* effect) {
  flutter::EncodableList files;
  if (HasFiles(data)) {
    AddFilePaths(data, &files);
  }
  if (effect != nullptr) {
    *effect = files.empty() ? DROPEFFECT_NONE : DROPEFFECT_COPY;
  }
  if (channel_ != nullptr) {
    channel_->InvokeMethod("performOperation",
                           std::make_unique<flutter::EncodableValue>(files));
  }
  return S_OK;
}
