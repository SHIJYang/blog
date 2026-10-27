#ifndef RUNNER_FILE_DROP_TARGET_H_
#define RUNNER_FILE_DROP_TARGET_H_

#include <flutter/encodable_value.h>
#include <flutter/method_channel.h>
#include <flutter/standard_method_codec.h>
#include <ole2.h>
#include <windows.h>

#include <memory>

// Native OLE IDropTarget for file drag & drop.
//
// desktop_drop registers its drop target from the plugin registrar, using
// registrar->GetView() to obtain the window handle. On some Flutter Windows
// versions that view lookup returns null, RegisterDragDrop is never called and
// the plugin silently stops working: dragging a file over the window does
// nothing.
//
// This class registers an IDropTarget on the Flutter view window directly and
// forwards drag events to Dart using the desktop_drop channel protocol
// (entered / updated / exited / performOperation), so no Dart side change is
// required.
class FileDropTarget : public IDropTarget {
 public:
  explicit FileDropTarget(flutter::BinaryMessenger* messenger);
  ~FileDropTarget();

  // Registers on |window|. Returns false when another drop target is already
  // registered for that window (the plugin works) or registration failed.
  bool Register(HWND window);
  void Unregister();

  // IUnknown
  HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid,
                                           void** object) override;
  ULONG STDMETHODCALLTYPE AddRef() override;
  ULONG STDMETHODCALLTYPE Release() override;

  // IDropTarget
  HRESULT STDMETHODCALLTYPE DragEnter(IDataObject* data,
                                      DWORD key_state,
                                      POINTL point,
                                      DWORD* effect) override;
  HRESULT STDMETHODCALLTYPE DragOver(DWORD key_state,
                                     POINTL point,
                                     DWORD* effect) override;
  HRESULT STDMETHODCALLTYPE DragLeave() override;
  HRESULT STDMETHODCALLTYPE Drop(IDataObject* data,
                                 DWORD key_state,
                                 POINTL point,
                                 DWORD* effect) override;

 private:
  void SendLocation(const char* method, POINTL point);
  static bool HasFiles(IDataObject* data);
  static void AddFilePaths(IDataObject* data, flutter::EncodableList* files);

  std::unique_ptr<flutter::MethodChannel<flutter::EncodableValue>> channel_;
  HWND window_ = nullptr;
  // Owned by the unique_ptr in FlutterWindow; Release() never deletes.
  LONG ref_count_ = 1;
};

#endif  // RUNNER_FILE_DROP_TARGET_H_
