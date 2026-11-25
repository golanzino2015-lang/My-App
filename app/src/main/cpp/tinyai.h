// Tiny AI: a GPT-style transformer trained from scratch on the phone's CPU cores.
#pragma once
#include <stdint.h>

typedef struct Model Model;

typedef struct {
    int V, C, L, H, T;
} Config;

Model *ta_create(Config cfg, uint32_t seed, int threads);
void ta_free(Model *m);
Config ta_config(const Model *m);
long ta_num_params(const Model *m);
long ta_step(const Model *m);
int ta_threads(const Model *m);

/** Grow the word table to newV words, keeping everything learned. */
int ta_grow_vocab(Model *m, int newV);

/** Training data: one long token stream plus the start of each conversation. */
void ta_set_data(Model *m, const int32_t *stream, long n, const int32_t *starts, long nstarts);

/** One learning step on `seqs` random sequences spread over all threads. Returns mean loss. */
float ta_train_step(Model *m, int seqs, float lr, float weight_decay);

/**
 * Continue `ctx` (n tokens) with up to max_new tokens. Stops at stop_id. Tokens >= vocab_used and
 * those in banned are never produced. temperature <= 0 means always pick the most likely word.
 * Returns the number of tokens written to out.
 */
int ta_generate(Model *m, const int32_t *ctx, int n, int32_t *out, int max_new, int stop_id,
                int vocab_used, const int32_t *banned, int nbanned, float temperature, int top_k,
                uint64_t seed);

int ta_save(const Model *m, const char *path);
Model *ta_load(const char *path, int threads);

/** For tests: mean loss of a forward pass on given inputs/targets, and gradient access. */
float ta_debug_loss(Model *m, const int32_t *inp, const int32_t *tgt, int T, int with_backward);
float *ta_debug_params(Model *m);
float *ta_debug_grads(Model *m);
