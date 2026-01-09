#!/usr/bin/env python3
"""
Persistent AI Engine Wrapper (Hardened Version)
Giữ AI engine sống suốt thời gian chạy, nhận commands từ stdin và trả kết quả qua stdout
"""

import sys
import json
import os
import traceback

# --- CẤU HÌNH QUAN TRỌNG: Ép buộc Line Buffering ---
# Điều này đảm bảo mọi lệnh print() đều được gửi đi NGAY LẬP TỨC
# mà không cần gọi flush=True thủ công mọi lúc.
try:
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(line_buffering=True)
        sys.stderr.reconfigure(line_buffering=True)
except Exception:
    pass # Python cũ hơn 3.7 không hỗ trợ reconfigure, sẽ dựa vào flush()

# Add current directory to path
script_dir = os.path.dirname(os.path.abspath(__file__))
if script_dir not in sys.path:
    sys.path.insert(0, script_dir)

def log_err(msg):
    """Ghi log ra stderr (để C++ hứng log debug)"""
    sys.stderr.write(f"[AI Wrapper] {msg}\n")
    sys.stderr.flush()

def send_response(msg):
    """Gửi phản hồi ra stdout (để C++ nhận dữ liệu)"""
    sys.stdout.write(f"{msg}\n")
    sys.stdout.flush()

def main():
    ai = None
    try:
        log_err("Starting initialization...")
        
        # 1. Import module AI
        try:
            from ai import AI, AIDifficulty
            log_err("Module 'ai' imported successfully.")
        except ImportError as e:
            log_err(f"CRITICAL: Failed to import 'ai' module: {e}")
            log_err(traceback.format_exc())
            send_response(f"error: import failed: {e}")
            return

        # 2. Khởi tạo Object AI
        ai = AI()
        
        # 3. Tìm đường dẫn Pikafish
        pikafish_path = os.path.join(script_dir, "pikafish")
        
        # Check quyền thực thi (quan trọng trong Docker)
        if os.path.exists(pikafish_path):
            if not os.access(pikafish_path, os.X_OK):
                log_err(f"WARNING: '{pikafish_path}' exists but is not executable. Trying to chmod +x...")
                try:
                    os.chmod(pikafish_path, 0o755)
                    log_err("chmod successful.")
                except Exception as ex:
                    log_err(f"chmod failed: {ex}")
        else:
            # Fallback tìm trong PATH hệ thống
            import shutil
            sys_path = shutil.which("pikafish")
            if sys_path:
                pikafish_path = sys_path
                log_err(f"Found pikafish in system PATH: {sys_path}")
            else:
                log_err("CRITICAL: Pikafish binary not found!")
                send_response("error: pikafish not found")
                return

        log_err(f"Using pikafish at: {pikafish_path}")

        # 4. Khởi động Engine
        if not ai.initialize(pikafish_path):
            log_err("CRITICAL: ai.initialize() returned False")
            send_response("error: failed to initialize")
            return

        # 5. Báo hiệu Sẵn sàng cho C++
        log_err("Initialization complete. Sending ready signal.")
        send_response("ready")

    except Exception as e:
        log_err(f"Exception during startup: {e}")
        log_err(traceback.format_exc())
        send_response(f"error: startup exception: {e}")
        return

    # --- VÒNG LẶP CHÍNH XỬ LÝ REQUEST ---
    log_err("Entering command loop...")
    
    try:
        # Đọc từng dòng từ stdin (C++ gửi request dạng line)
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue
            
            # Xử lý lệnh thoát
            if line == "quit":
                log_err("Received quit command. Exiting.")
                break
            
            try:
                # Parse JSON
                # input format: {"fen": "...", "difficulty": "medium"}
                req = json.loads(line)
                fen = req.get("fen", "")
                difficulty_str = req.get("difficulty", "medium")
                
                if not fen:
                    log_err("Received request with empty FEN.")
                    send_response("error")
                    continue

                # Map difficulty string -> Enum
                difficulty_map = {
                    "easy": AIDifficulty.EASY,
                    "medium": AIDifficulty.MEDIUM,
                    "hard": AIDifficulty.HARD
                }
                difficulty = difficulty_map.get(difficulty_str.lower(), AIDifficulty.MEDIUM)

                # Gọi AI dự đoán
                log_err(f"Thinking... (Diff: {difficulty_str}, FEN: {fen[:50]}...)") 
                move = ai.predict_move(fen, difficulty)

                if move:
                    # Convert Move object -> UCI string (e.g., "b0c2")
                    uci_move = AI.move_to_uci(move)
                    log_err(f"Move found: {uci_move}, sending response...")
                    send_response(uci_move)
                    log_err(f"Response sent: {uci_move}")
                else:
                    log_err("AI returned no move (predict_move returned None).")
                    send_response("error")

            except json.JSONDecodeError:
                log_err(f"Invalid JSON received: {line}")
                send_response("error")
            except Exception as e:
                log_err(f"Error processing request: {e}")
                send_response("error")

    except KeyboardInterrupt:
        log_err("KeyboardInterrupt received.")
    finally:
        if ai:
            log_err("Shutting down AI engine...")
            ai.shutdown()
        log_err("Wrapper terminated.")

if __name__ == "__main__":
    main()