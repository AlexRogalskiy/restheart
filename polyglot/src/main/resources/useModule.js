/* How to run:
$ cd plugins
$ npm init
$ npm install moment
*/

const moment = require('moment');

({
    options: {
        name: "jsModule",
        description: "just an example JavaScript service that uses a CommonJS module ",
        uri: '/jsModule',
        secured: false, // optional, default false
        matchPolicy: "PREFIX" // optional, default PREFIX
    },

    handle: (request, response) => {
        LOGGER.debug('request {}', request.getContent());
        const rc = JSON.parse(new String(request.getContent()));

        let body = {
            msg: `Hello ${rc.name }`,
            date: moment().format("[Today is] dddd")
        }

        response.setContent(JSON.stringify(body));
        response.setContentTypeAsJson();
    }
})