#include "../../include/ai/ai_service.h"
#include <array>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <errno.h>
#include <fstream>
#include <iostream>
#include <memory>
#include <sstream>
#include <vector>
#include <sys/wait.h>
#include <sys/select.h>
#include <unistd.h>
#include <fcntl.h>

using namespace std;

AIService::AIService()
    : pythonPath("python3"), aiScriptPath(""), initialized(false),
      aiProcessStdin(nullptr), aiProcessStdout(nullptr), aiProcessStderr(nullptr), aiProcessPid(0) {}

AIService::~AIService() {
  cleanupProcess();
}

bool AIService::initialize(const string &python, const string &aiScriptDir) {
  pythonPath = python.empty() ? "python3" : python;
  
  cout << "[AIService] Initializing AI service..." << endl;
  cout << "[AIService] Python path: " << pythonPath << endl;
  cout << "[AIService] AI script dir: " << (aiScriptDir.empty() ? "(auto-detect)" : aiScriptDir) << endl;

  // Find AI script path
  if (!aiScriptDir.empty()) {
    aiScriptPath = aiScriptDir + "/ai.py";
  } else {
    // Try common locations
    vector<string> searchPaths = {"/opt/app/AI/ai.py", // Docker container path
                                  "../AI/ai.py", "../../AI/ai.py",
                                  "../../../AI/ai.py", "./AI/ai.py"};

    cout << "[AIService] Searching for ai.py in:" << endl;
    for (const auto &path : searchPaths) {
      cout << "  - " << path;
      ifstream f(path);
      if (f.good()) {
        aiScriptPath = path;
        cout << " [FOUND]" << endl;
        break;
      }
      cout << " [not found]" << endl;
    }
  }

  // Verify AI script exists
  if (aiScriptPath.empty()) {
    cerr << "[AIService] Could not find ai.py" << endl;
    return false;
  }

  ifstream f(aiScriptPath);
  if (!f.good()) {
    cerr << "[AIService] AI script not found at: " << aiScriptPath << endl;
    return false;
  }

  // Test Python availability and get absolute path
  string testCmd = pythonPath + " --version 2>&1";
  string result = "";

  array<char, 128> buffer;
  unique_ptr<FILE, decltype(&pclose)> pipe(popen(testCmd.c_str(), "r"), pclose);
  if (pipe) {
    while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
      result += buffer.data();
    }
  }

  if (result.find("Python") == string::npos) {
    cerr << "[AIService] Python not available at: " << pythonPath << endl;
    return false;
  }

  // Get absolute path of python3
  // Try multiple methods to find python3
  vector<string> pythonPaths = {
    "/usr/bin/python3",
    "/usr/local/bin/python3",
    "/bin/python3",
    pythonPath  // Keep original as fallback
  };
  
  string pythonAbsPath = "";
  for (const auto& testPath : pythonPaths) {
    if (access(testPath.c_str(), X_OK) == 0) {
      pythonAbsPath = testPath;
      break;
    }
  }
  
  // If not found in common paths, try which/command
  if (pythonAbsPath.empty()) {
    string whichCmd = "which " + pythonPath + " 2>/dev/null || command -v " + pythonPath + " 2>/dev/null";
    unique_ptr<FILE, decltype(&pclose)> whichPipe(popen(whichCmd.c_str(), "r"), pclose);
    if (whichPipe) {
      while (fgets(buffer.data(), buffer.size(), whichPipe.get()) != nullptr) {
        pythonAbsPath += buffer.data();
      }
    }
    
    // Trim whitespace
    while (!pythonAbsPath.empty() && (pythonAbsPath.back() == '\n' || pythonAbsPath.back() == '\r' || pythonAbsPath.back() == ' ')) {
      pythonAbsPath.pop_back();
    }
  }
  
  if (!pythonAbsPath.empty()) {
    pythonPath = pythonAbsPath;
    cout << "[AIService] Using Python at: " << pythonPath << endl;
  } else {
    cerr << "[AIService] Warning: Could not find absolute path for " << pythonPath << ", trying common paths" << endl;
    pythonPath = "/usr/bin/python3";  // Default fallback
  }

  // Test Pikafish availability by checking if it exists
  string pikafishTestCmd =
      "which pikafish 2>/dev/null || command -v pikafish 2>/dev/null";
  string pikafishResult = "";

  array<char, 128> pikaBuf;
  unique_ptr<FILE, decltype(&pclose)> pikaPipe(
      popen(pikafishTestCmd.c_str(), "r"), pclose);
  if (pikaPipe) {
    while (fgets(pikaBuf.data(), pikaBuf.size(), pikaPipe.get()) != nullptr) {
      pikafishResult += pikaBuf.data();
    }
  }

  // Also check common locations
  vector<string> pikafishPaths = {
      "/opt/app/AI/pikafish", // Docker container path
      "/opt/pikafish/pikafish", // Docker built pikafish
      "/usr/local/bin/pikafish",
      "/usr/bin/pikafish",
      "./pikafish",
      "../pikafish",
      "./AI/pikafish",
      "../AI/pikafish"};

  bool pikafishFound = !pikafishResult.empty();
  if (!pikafishFound) {
    for (const auto &path : pikafishPaths) {
      ifstream pf(path);
      if (pf.good()) {
        pikafishFound = true;
        break;
      }
    }
  }

  if (!pikafishFound) {
    cerr << "[AIService] Pikafish engine not found. AI features will be "
            "unavailable."
         << endl;
    cerr << "[AIService] Searched paths:" << endl;
    for (const auto& path : pikafishPaths) {
      cerr << "  - " << path << endl;
    }
    cerr << "[AIService] Install Pikafish from: "
            "https://github.com/official-pikafish/Pikafish"
         << endl;
    initialized = false;
    return false;
  } else {
    cout << "[AIService] Pikafish found" << endl;
  }

  // Find ai_persistent_wrapper.py
  string aiWrapperPath = aiScriptPath.substr(0, aiScriptPath.rfind('/')) + "/ai_persistent_wrapper.py";
  ifstream wrapperFile(aiWrapperPath);
  if (!wrapperFile.good()) {
    cerr << "[AIService] ai_persistent_wrapper.py not found at: " << aiWrapperPath << endl;
    cerr << "[AIService] aiScriptPath: " << aiScriptPath << endl;
    cerr << "[AIService] Trying alternative paths..." << endl;
    // Try alternative paths
    vector<string> altPaths = {
      "/opt/app/AI/ai_persistent_wrapper.py",
      "../AI/ai_persistent_wrapper.py",
      "../../AI/ai_persistent_wrapper.py",
      "./AI/ai_persistent_wrapper.py"
    };
    bool found = false;
    for (const auto& path : altPaths) {
      ifstream altFile(path);
      if (altFile.good()) {
        aiWrapperPath = path;
        found = true;
        cout << "[AIService] Found ai_persistent_wrapper.py at: " << aiWrapperPath << endl;
        break;
      }
    }
    if (!found) {
      cerr << "[AIService] ai_persistent_wrapper.py not found in any location" << endl;
      initialized = false;
      return false;
    }
  } else {
    cout << "[AIService] Found ai_persistent_wrapper.py at: " << aiWrapperPath << endl;
  }

  // Spawn persistent Python process
  int stdin_pipe[2], stdout_pipe[2], stderr_pipe[2];
  if (::pipe(stdin_pipe) < 0 || ::pipe(stdout_pipe) < 0 || ::pipe(stderr_pipe) < 0) {
    cerr << "[AIService] Failed to create pipes" << endl;
    initialized = false;
    return false;
  }

  aiProcessPid = fork();
  if (aiProcessPid < 0) {
    cerr << "[AIService] Failed to fork process" << endl;
    close(stdin_pipe[0]);
    close(stdin_pipe[1]);
    close(stdout_pipe[0]);
    close(stdout_pipe[1]);
    close(stderr_pipe[0]);
    close(stderr_pipe[1]);
    initialized = false;
    return false;
  }

  if (aiProcessPid == 0) {
    // Child process: redirect stdin/stdout/stderr and exec Python script
    close(stdin_pipe[1]);   // Close write end of stdin pipe
    close(stdout_pipe[0]);   // Close read end of stdout pipe
    close(stderr_pipe[0]);   // Close read end of stderr pipe

    dup2(stdin_pipe[0], STDIN_FILENO);
    dup2(stdout_pipe[1], STDOUT_FILENO);
    dup2(stderr_pipe[1], STDERR_FILENO);  // Keep stderr separate for debugging

    close(stdin_pipe[0]);
    close(stdout_pipe[1]);
    close(stderr_pipe[1]);

    // Verify files exist before exec
    if (access(pythonPath.c_str(), X_OK) != 0) {
      cerr << "[AIService] Python not executable: " << pythonPath << " - " << strerror(errno) << endl;
      _exit(1);
    }
    if (access(aiWrapperPath.c_str(), R_OK) != 0) {
      cerr << "[AIService] Script not readable: " << aiWrapperPath << " - " << strerror(errno) << endl;
      _exit(1);
    }
    
    // Use execl with absolute paths
    // execl(path, argv0, arg1, ..., NULL)
    // argv0 is the program name (can be just "python3" or the path)
    // Note: Don't use cout here - stdout is already redirected to pipe
    execl(pythonPath.c_str(), "python3", aiWrapperPath.c_str(), (char*)NULL);
    cerr << "[AIService] Failed to exec Python script: " << strerror(errno) << endl;
    cerr << "[AIService] pythonPath: " << pythonPath << endl;
    cerr << "[AIService] aiWrapperPath: " << aiWrapperPath << endl;
    _exit(1);
  } else {
    // Parent process: keep pipes for communication
    close(stdin_pipe[0]);   // Close read end of stdin pipe
    close(stdout_pipe[1]);  // Close write end of stdout pipe
    close(stderr_pipe[1]);  // Close write end of stderr pipe

    aiProcessStdin = fdopen(stdin_pipe[1], "w");
    aiProcessStdout = fdopen(stdout_pipe[0], "r");
    aiProcessStderr = fdopen(stderr_pipe[0], "r");

    if (!aiProcessStdin || !aiProcessStdout) {
      cerr << "[AIService] Failed to open pipe streams" << endl;
      if (aiProcessStderr) fclose(aiProcessStderr);
      cleanupProcess();
      initialized = false;
      return false;
    }

    // Wait for "ready" signal from Python process
    char buffer[256];
    char stderrBuffer[256];
    memset(buffer, 0, sizeof(buffer));
    cout << "[AIService] Waiting for Python process to be ready..." << endl;
    
    // Get file descriptors for select()
    int stdoutFd = fileno(aiProcessStdout);
    int stderrFd = aiProcessStderr ? fileno(aiProcessStderr) : -1;
    
    // Set non-blocking mode for stdout
    int flags = fcntl(stdoutFd, F_GETFL, 0);
    fcntl(stdoutFd, F_SETFL, flags | O_NONBLOCK);
    if (stderrFd >= 0) {
      flags = fcntl(stderrFd, F_GETFL, 0);
      fcntl(stderrFd, F_SETFL, flags | O_NONBLOCK);
    }
    
    // Read with timeout (15 seconds - Python may need time to initialize Pikafish)
    int attempts = 0;
    bool foundReady = false;
    while (attempts < 150) {  // 150 * 100ms = 15 seconds
      // Check if process is still alive
      int status;
      if (waitpid(aiProcessPid, &status, WNOHANG) != 0) {
        cerr << "[AIService] Python process died before ready (status=" << status << ")" << endl;
        // Read any remaining stderr
        if (aiProcessStderr) {
          while (fgets(stderrBuffer, sizeof(stderrBuffer), aiProcessStderr) != nullptr) {
            cerr << "[AIService Python stderr]: " << stderrBuffer;
          }
        }
        break;
      }
      
      // Use select() to check if data is available (non-blocking)
      fd_set readfds;
      FD_ZERO(&readfds);
      FD_SET(stdoutFd, &readfds);
      if (stderrFd >= 0) {
        FD_SET(stderrFd, &readfds);
      }
      
      struct timeval timeout;
      timeout.tv_sec = 0;
      timeout.tv_usec = 100000;  // 100ms
      
      int maxFd = (stderrFd >= 0 && stderrFd > stdoutFd) ? stderrFd : stdoutFd;
      int selectResult = select(maxFd + 1, &readfds, nullptr, nullptr, &timeout);
      
      if (selectResult > 0) {
        // Read from stdout if available
        if (FD_ISSET(stdoutFd, &readfds)) {
          if (fgets(buffer, sizeof(buffer), aiProcessStdout) != nullptr) {
            cout << "[AIService] Received from Python stdout: " << buffer;
            if (strstr(buffer, "ready") != nullptr) {
              cout << "[AIService] Persistent AI engine ready" << endl;
              foundReady = true;
              break;
            }
          }
        }
        
        // Read from stderr if available
        if (stderrFd >= 0 && FD_ISSET(stderrFd, &readfds)) {
          if (fgets(stderrBuffer, sizeof(stderrBuffer), aiProcessStderr) != nullptr) {
            cerr << "[AIService Python stderr]: " << stderrBuffer;
          }
        }
      } else if (selectResult < 0) {
        // Error in select
        if (errno != EINTR) {
          cerr << "[AIService] select() error: " << strerror(errno) << endl;
        }
      }
      
      attempts++;
      
      // Print progress every 2 seconds
      if (attempts % 20 == 0) {
        cout << "[AIService] Still waiting... (" << (attempts * 100 / 1000) << "s)" << endl;
      }
    }
    
    if (foundReady) {
      initialized = true;
      return true;
    }
    
    cerr << "[AIService] Timeout waiting for AI engine to be ready" << endl;
    cerr << "[AIService] Last received: " << (buffer[0] ? buffer : "(empty)") << endl;
    cerr << "[AIService] Attempts: " << attempts << " (timeout: 15s)" << endl;

    cerr << "[AIService] Timeout waiting for AI engine to be ready" << endl;
    cleanupProcess();
    initialized = false;
    return false;
  }
}

bool AIService::isReady() const { return initialized; }

string AIService::difficultyToString(AIDifficulty difficulty) {
  switch (difficulty) {
  case AIDifficulty::EASY:
    return "easy";
  case AIDifficulty::MEDIUM:
    return "medium";
  case AIDifficulty::HARD:
    return "hard";
  default:
    return "medium";
  }
}

string AIService::executePythonAI(const string &xfen,
                                  const string &difficulty) {
  if (!initialized || !aiProcessStdin || !aiProcessStdout) {
    cerr << "[AIService::executePythonAI] Not initialized or process not ready" << endl;
    return "error";
  }

  // Check if process is still alive
  int status;
  pid_t waitResult = waitpid(aiProcessPid, &status, WNOHANG);
  if (waitResult != 0) {
    cerr << "[AIService::executePythonAI] AI process has died (status=" << status << ")" << endl;
    // Try to read stderr to see what happened
    if (aiProcessStderr) {
      char errBuffer[256];
      while (fgets(errBuffer, sizeof(errBuffer), aiProcessStderr) != nullptr) {
        cerr << "[AIService Python stderr]: " << errBuffer;
      }
    }
    cleanupProcess();
    initialized = false;
    return "error";
  }

  // Send JSON request to persistent process
  nlohmann::json request;
  request["fen"] = xfen;
  request["difficulty"] = difficulty;

  string requestStr = request.dump() + "\n";
  
  cout << "[AIService::executePythonAI] Sending request: " << requestStr << endl;
  
  if (fprintf(aiProcessStdin, "%s", requestStr.c_str()) < 0) {
    cerr << "[AIService::executePythonAI] Failed to send request: " << strerror(errno) << endl;
    return "error";
  }
  if (fflush(aiProcessStdin) != 0) {
    cerr << "[AIService::executePythonAI] Failed to flush stdin: " << strerror(errno) << endl;
    return "error";
  }
  
  cout << "[AIService::executePythonAI] Request sent, waiting for response..." << endl;

  // Read response from persistent process with timeout (30 seconds for hard difficulty)
  char buffer[256];
  string result = "";
  
  int stdoutFd = fileno(aiProcessStdout);
  int flags = fcntl(stdoutFd, F_GETFL, 0);
  fcntl(stdoutFd, F_SETFL, flags | O_NONBLOCK);
  
  // Wait for response with timeout (tăng timeout để đảm bảo đủ thời gian cho AI tính toán)
  // Python AI timeout: easy=5s, medium=10s, hard=17s
  // Backend timeout nên lớn hơn Python timeout + buffer
  int timeoutSeconds = (difficulty == "hard") ? 25 : (difficulty == "medium") ? 15 : 8;
  int attempts = 0;
  bool gotResponse = false;
  
  // Đọc nhiều dòng để tránh đọc nhầm dòng log (nếu có)
  // Python wrapper chỉ gửi một dòng kết quả, nhưng có thể có dòng trống hoặc log
  vector<string> responseLines;
  
  while (attempts < timeoutSeconds * 10) {  // Check every 100ms
    fd_set readfds;
    FD_ZERO(&readfds);
    FD_SET(stdoutFd, &readfds);
    
    struct timeval timeout;
    timeout.tv_sec = 0;
    timeout.tv_usec = 100000;  // 100ms
    
    int selectResult = select(stdoutFd + 1, &readfds, nullptr, nullptr, &timeout);
    
    if (selectResult > 0 && FD_ISSET(stdoutFd, &readfds)) {
      // Data available, read it
      if (fgets(buffer, sizeof(buffer), aiProcessStdout) != nullptr) {
        string line(buffer);
        // Trim whitespace
        while (!line.empty() && (line.back() == '\n' || line.back() == '\r' || 
                                 line.back() == ' ' || line.back() == '\t')) {
          line.pop_back();
        }
        
        if (!line.empty()) {
          responseLines.push_back(line);
          cout << "[AIService::executePythonAI] Received line: " << line << endl;
          
          // Kiểm tra xem có phải là kết quả hợp lệ không (UCI move hoặc "error")
          // UCI move: 4 ký tự (a0a1) hoặc "error"
          if (line.length() == 4 && isalpha(line[0]) && isdigit(line[1]) && 
              isalpha(line[2]) && isdigit(line[3])) {
            // Valid UCI move
            result = line;
            gotResponse = true;
            break;
          } else if (line == "error") {
            // Error response
            result = "error";
            gotResponse = true;
            break;
          }
          // Nếu không phải kết quả hợp lệ, tiếp tục đọc (có thể là log hoặc dòng trống)
        }
      } else {
        // EOF or error - nếu đã có response lines, dùng dòng cuối cùng
        if (!responseLines.empty()) {
          result = responseLines.back();
          // Kiểm tra xem có phải kết quả hợp lệ không
          if (result.length() == 4 && isalpha(result[0]) && isdigit(result[1]) && 
              isalpha(result[2]) && isdigit(result[3])) {
            gotResponse = true;
          } else if (result == "error") {
            gotResponse = true;
          }
        }
        break;
      }
    } else if (selectResult < 0 && errno != EINTR) {
      cerr << "[AIService::executePythonAI] select() error: " << strerror(errno) << endl;
      break;
    }
    
    // Check if process died
    if (waitpid(aiProcessPid, nullptr, WNOHANG) != 0) {
      cerr << "[AIService::executePythonAI] AI process died during request" << endl;
      cleanupProcess();
      initialized = false;
      result = "error";
      break;
    }
    
    attempts++;
  }
  
  // Nếu chưa có response hợp lệ nhưng có response lines, thử dùng dòng cuối
  if (!gotResponse && !responseLines.empty()) {
    result = responseLines.back();
    if (result.length() == 4 && isalpha(result[0]) && isdigit(result[1]) && 
        isalpha(result[2]) && isdigit(result[3])) {
      gotResponse = true;
      cout << "[AIService::executePythonAI] Using last response line as result: " << result << endl;
    } else if (result == "error") {
      gotResponse = true;
    }
  }
  
  // Restore blocking mode
  fcntl(stdoutFd, F_SETFL, flags);
  
  if (!gotResponse) {
    cerr << "[AIService::executePythonAI] Timeout waiting for response (timeout: " 
         << timeoutSeconds << "s)" << endl;
    result = "error";
  }

  cout << "[AIService::executePythonAI] Request: fen=" << xfen.substr(0, 50) 
       << "..., difficulty=" << difficulty << ", Result: " << result << endl;

  return result;
}

optional<AIMove> AIService::parseUCIMove(const string &uci) {
  if (uci.length() < 4 || uci == "error") {
    return nullopt;
  }

  // UCI format: "a0a1" where a=col(0-8), 0=row(0-9)
  // IMPORTANT: Pikafish UCI uses FEN coordinate system:
  // - Row 0 in UCI = top/black side (row 9 in board)
  // - Row 9 in UCI = bottom/red side (row 0 in board)
  // We need to flip: boardRow = 9 - uciRow
  char fromColChar = uci[0];
  char fromRowChar = uci[1];
  char toColChar = uci[2];
  char toRowChar = uci[3];

  if (!isalpha(fromColChar) || !isalpha(toColChar) || !isdigit(fromRowChar) ||
      !isdigit(toRowChar)) {
    return nullopt;
  }

  int uciFromRow = fromRowChar - '0';
  int uciToRow = toRowChar - '0';

  AIMove move;
  move.fromY = fromColChar - 'a'; // col: a=0, b=1, ..., i=8
  move.fromX = 9 - uciFromRow; // Flip row: UCI row 0 (top) = board row 9, UCI
                               // row 9 (bottom) = board row 0
  move.toY = toColChar - 'a';
  move.toX = 9 - uciToRow; // Flip row
  move.uci = uci;

  // Validate ranges
  if (move.fromX < 0 || move.fromX > 9 || move.fromY < 0 || move.fromY > 8 ||
      move.toX < 0 || move.toX > 9 || move.toY < 0 || move.toY > 8) {
    return nullopt;
  }

  return move;
}

AIResult AIService::predictMove(const string &xfen, AIDifficulty difficulty) {
  AIResult result;

  if (!initialized) {
    result.success = false;
    result.message = "AI service not initialized";
    return result;
  }

  string diffStr = difficultyToString(difficulty);
  string uciResult = executePythonAI(xfen, diffStr);

  if (uciResult.empty() || uciResult == "error") {
    result.success = false;
    result.message = "AI engine failed to find a move";
    return result;
  }

  auto move = parseUCIMove(uciResult);
  if (!move.has_value()) {
    result.success = false;
    result.message = "Failed to parse AI move: " + uciResult;
    return result;
  }

  result.success = true;
  result.message = "Move predicted successfully";
  result.move = move;
  return result;
}

AIResult AIService::predictMoveWithHistory(const string &initialXfen,
                                           const vector<string> &moves,
                                           AIDifficulty difficulty) {
  AIResult result;

  if (!initialized) {
    result.success = false;
    result.message = "AI service not initialized";
    return result;
  }

  // Build position string with move history
  stringstream positionStr;
  positionStr << "position fen " << initialXfen;

  if (!moves.empty()) {
    positionStr << " moves";
    for (const auto &m : moves) {
      positionStr << " " << m;
    }
  }

  // For position with history, we need different Python command
  string diffStr = difficultyToString(difficulty);
  stringstream cmd;
  cmd << pythonPath << " -c \"";
  cmd << "import sys; sys.path.insert(0, '"
      << aiScriptPath.substr(0, aiScriptPath.rfind('/')) << "'); ";
  cmd << "from ai import AI, AIDifficulty; ";
  cmd << "ai = AI(); ";
  cmd << "if ai.initialize(): ";
  cmd << "  move = ai.predict_move('" << positionStr.str() << "', AIDifficulty."
      << (difficulty == AIDifficulty::EASY     ? "EASY"
          : difficulty == AIDifficulty::MEDIUM ? "MEDIUM"
                                               : "HARD")
      << "); ";
  cmd << "  if move: print(AI.move_to_uci(move)); ";
  cmd << "  else: print('error'); ";
  cmd << "  ai.shutdown(); ";
  cmd << "else: print('error')";
  cmd << "\" 2>&1";

  string uciResult = "";
  array<char, 256> buffer;
  unique_ptr<FILE, decltype(&pclose)> pipe(popen(cmd.str().c_str(), "r"),
                                           pclose);

  if (pipe) {
    while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
      uciResult += buffer.data();
    }
  }

  // Trim whitespace
  while (!uciResult.empty() &&
         (uciResult.back() == '\n' || uciResult.back() == '\r' ||
          uciResult.back() == ' ')) {
    uciResult.pop_back();
  }

  if (uciResult.empty() || uciResult == "error") {
    result.success = false;
    result.message = "AI engine failed to find a move";
    return result;
  }

  auto move = parseUCIMove(uciResult);
  if (!move.has_value()) {
    result.success = false;
    result.message = "Failed to parse AI move: " + uciResult;
    return result;
  }

  result.success = true;
  result.message = "Move predicted successfully";
  result.move = move;
  return result;
}

AIResult AIService::suggestMove(const string &xfen) {
  return predictMove(xfen, AIDifficulty::HARD);
}

string AIService::toUCI(int fromX, int fromY, int toX, int toY) {
  char fromCol = 'a' + fromY;
  char fromRow = '0' + fromX;
  char toCol = 'a' + toY;
  char toRow = '0' + toX;

  return string(1, fromCol) + string(1, fromRow) + string(1, toCol) +
         string(1, toRow);
}

optional<AIMove> AIService::fromUCI(const string &uci) {
  if (uci.length() < 4) {
    return nullopt;
  }

  // UCI format uses FEN coordinate system (row 0 = top/black, row 9 =
  // bottom/red) Need to flip to board coordinates (row 0 = bottom/red, row 9 =
  // top/black)
  int uciFromRow = uci[1] - '0';
  int uciToRow = uci[3] - '0';

  AIMove move;
  move.fromY = uci[0] - 'a';
  move.fromX = 9 - uciFromRow; // Flip row
  move.toY = uci[2] - 'a';
  move.toX = 9 - uciToRow; // Flip row
  move.uci = uci;

  if (move.fromX < 0 || move.fromX > 9 || move.fromY < 0 || move.fromY > 8 ||
      move.toX < 0 || move.toX > 9 || move.toY < 0 || move.toY > 8) {
    return nullopt;
  }

  return move;
}

string AIService::boardToXfen(const string board[10][9], const string &turn) {
  stringstream fen;

  // Convert from row 9 to row 0 (FEN order)
  for (int row = 9; row >= 0; row--) {
    int emptyCount = 0;
    for (int col = 0; col < 9; col++) {
      string piece = board[row][col];
      if (piece.empty() || piece == " ") {
        emptyCount++;
      } else {
        if (emptyCount > 0) {
          fen << emptyCount;
          emptyCount = 0;
        }
        fen << piece;
      }
    }
    if (emptyCount > 0) {
      fen << emptyCount;
    }
    if (row > 0) {
      fen << "/";
    }
  }

  fen << " " << turn << " - - 0 1";
  return fen.str();
}

void AIService::cleanupProcess() {
  if (aiProcessStdin) {
    // Send quit command to Python process
    fprintf(aiProcessStdin, "quit\n");
    fflush(aiProcessStdin);
    fclose(aiProcessStdin);
    aiProcessStdin = nullptr;
  }

  if (aiProcessStdout) {
    fclose(aiProcessStdout);
    aiProcessStdout = nullptr;
  }

  if (aiProcessPid > 0) {
    // Wait for process to terminate (with timeout)
    int status;
    int waitResult = waitpid(aiProcessPid, &status, WNOHANG);
    if (waitResult == 0) {
      // Process still running, kill it
      kill(aiProcessPid, SIGTERM);
      sleep(1);
      waitpid(aiProcessPid, &status, 0);
    }
    aiProcessPid = 0;
  }
}
