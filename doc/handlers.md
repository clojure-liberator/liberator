---
layout: default
title: Handlers
---
# Handlers

For every http status code there is a handler function defined in
liberator. All have sensible defaults and will return a simple english
error message or an empty reponse, whatever is appropriate.

Handler key                  | status code
-----------------------------|------------
                                handle-ok | 200
                           handle-created | 201
                           handle-options | 201
                          handle-accepted | 202
                        handle-no-content | 204
                 handle-moved-permanently | 301
                         handle-see-other | 303
                      handle-not-modified | 304
                 handle-moved-temporarily | 307
          handle-multiple-representations | 310
                         handle-malformed | 400
                      handle-unauthorized | 401
                         handle-forbidden | 403
                         handle-not-found | 404
                handle-method-not-allowed | 405
                    handle-not-acceptable | 406
                          handle-conflict | 409
                              handle-gone | 410
               handle-precondition-failed | 412
          handle-request-entity-too-large | 413
                      handle-uri-too-long | 414
            handle-unsupported-media-type | 415
                   handle-not-implemented | 501
                    handle-unknown-method | 501
             handle-service-not-available | 503