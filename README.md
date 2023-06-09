# statstrade-pay-public

This project implements the smart contract for the blockchain portion of the centralised clearing house. [Video](https://drive.google.com/file/d/18UvfLPTkdFgmhCFeTAbxViSfYX7BFTb8/view?usp=share_link) will give a brief overview of functionality.


## Running Tests - Docker

Make sure docker is installed


```
 mkdir test-dir
 cd    test-dir
 git clone https://github.com/zcaudate/foundation-base.git
 git clone https://github.com/statstrade-dev/statstrade-pay-public.git
 docker run --rm --network host -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd):$(pwd) -w $(pwd) zcaudate/foundation-ci:main bash -c 'cd statstrade-pay-public && make setup-checkouts && lein test-pay'

```

## Running Tests

Make sure node is installed

```
npm -g install ganache yarn crypto-js ethers@5.7.2 solc@0.8.17
mkdir test-dir
cd    test-dir
git clone https://github.com/zcaudate/foundation-base.git
git clone https://github.com/statstrade-dev/statstrade-pay-public.git
cd foundation-base && lein install && cd ..
cd statstrade-pay-public
make setup-checkouts && lein test-pay
```

## License

Copyright © 2023 Tahto Group

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
