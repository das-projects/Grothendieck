#include "DeliteCudaProfiler.h"

static BufferedFileWriter** profileWriters = new BufferedFileWriter*[DELITE_NUM_CUDA];
static std::stack< cudatimer_t >** timermaps = new std::stack< cudatimer_t >*[DELITE_NUM_CUDA];
static double appStartTime = 0;

static std::string profileFilePrefix;

double milliseconds(struct timeval t) {
  return double(t.tv_sec * 1000) + (double(t.tv_usec) / 1000);
}

void InitDeliteCudaTimer(int32_t tid, int32_t lowestCudaTid) {
  if (tid == 0) {
	struct timeval a;
    gettimeofday(&a,NULL);
    appStartTime = milliseconds(a);

    char* cwd = get_current_dir_name();
    std::stringstream ss;
    ss << cwd << "/profile/profile_t_";
    profileFilePrefix = ss.str();
  }

  timermaps[tid] = new std::stack< cudatimer_t >();
  std::stringstream ss; 
  ss << profileFilePrefix << ( lowestCudaTid + tid ) << ".csv";
  profileWriters[tid] = new BufferedFileWriter(ss.str().c_str());
}

void DeliteCudaTimerStart(int32_t tid, std::string name) {
  cudaDeviceSynchronize();

  struct timeval start;
  gettimeofday(&start,NULL);
  cudatimer_t timer = {start};

  timermaps[tid]->push(timer);
}

void DeliteCudaTimerStop(int32_t tid, std::string name, bool isMultiLoop) {
  cudaDeviceSynchronize();

  struct timeval stop;
  gettimeofday(&stop,NULL);

  double start = milliseconds( timermaps[tid]->top().start ); 
  double end = milliseconds( stop );
  double elapsedMillis = end - start;

  timermaps[tid]->pop();
  profileWriters[tid]->writeTimer( name, long(start - appStartTime), elapsedMillis, timermaps[tid]->size(), tid, isMultiLoop );
}

#ifndef __DELITE_CPP_PROFILER_H__ 

BufferedFileWriter::BufferedFileWriter(const char* fileName)
{
    fs.open(fileName);
}

void BufferedFileWriter::writeTimer(std::string kernel, long start, double duration, int32_t level, int32_t tid, bool isMultiLoop) {
  if (isMultiLoop) {
    fs << kernel << "_" << tid << "," << start << "," << duration << "," << level << std::endl;
  } else {
    fs << kernel << "," << start << "," << duration << "," << level << std::endl;
  }
}

void BufferedFileWriter::close() {
  fs.close();
}

#endif

void DeliteCudaTimerClose(int32_t tid, int32_t rid, JNIEnv* env) {
  for (int32_t i = 0; i < DELITE_NUM_CUDA; i++) {
    profileWriters[i]->close();
  }
}
