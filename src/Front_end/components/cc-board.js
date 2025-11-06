// /Users/tuanpham/Chinese-chess-nework-programming/src/Front_end/components/cc-board.js
class CCBoard extends HTMLElement{
	static get observedAttributes(){ return ['src','alt','state']; }
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
		root.innerHTML = `
			<style>
				:host{
					display:block;
					position: absolute;
					top: calc(var(--top, 0px) * var(--scale));
					left: calc(var(--left, 0px) * var(--scale));
					width: calc(var(--w, 640px) * var(--scale));
					transition: all .5s cubic-bezier(0.4, 0, 0.2, 1);
				}
				:host([state="small"]){
					top: calc(var(--top-small, 0px) * var(--scale));
					left: calc(var(--left-small, 0px) * var(--scale));
					width: calc(var(--w-small, 400px) * var(--scale));
				}
				.wrap{
					position: relative;
					display: inline-block;
					transition: transform .25s ease;
					overflow: visible;
					cursor: pointer;
				}
				.wrap:hover{ transform: translateY(-2px); }
				:host([state="small"]) .wrap:hover{ transform: translateY(-1px); }

				img{
					position: relative;
					z-index: 1;
					display:block;
					width:100%;
					height:auto;
				}

				svg{
					position:absolute;
					inset:0;
					width:100%;
					height:100%;
					pointer-events:none;
					z-index: 2;
					overflow: visible;
				}
				.ring{ opacity:0; transition: opacity .2s ease; }
				.wrap:hover .ring{ opacity:1; }
			</style>

			<div class="wrap" part="container">
				<img class="cc-img" part="img" />
				<svg class="ring" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
					<defs>
						<linearGradient id="cc-board-grad" x1="0" y1="0" x2="1" y2="1">
							<stop offset="0%" stop-color="rgba(255,243,174,.95)"/>
							<stop offset="100%" stop-color="rgba(255,215,64,.85)"/>
						</linearGradient>

						<filter id="cc-outline-filter"
							filterUnits="objectBoundingBox"
							x="-0.15" y="-0.15" width="1.30" height="1.30">
							<feColorMatrix in="SourceGraphic" type="matrix"
								values="0 0 0 0 0
										0 0 0 0 0
										0 0 0 0 0
										0 0 0 1 0" result="alpha"/>
							<feMorphology in="alpha" operator="dilate" radius="10" result="dilated"/>
							<feComposite in="dilated" in2="alpha" operator="out" result="ring"/>
						</filter>

						<mask id="cc-outline-mask">
							<image id="maskImg" href="" x="0" y="0" width="100%" height="100%"
								preserveAspectRatio="xMidYMid meet" filter="url(#cc-outline-filter)"/>
						</mask>
					</defs>

					<rect x="-5%" y="-5%" width="110%" height="110%"
						fill="url(#cc-board-grad)" mask="url(#cc-outline-mask)"/>
				</svg>
			</div>
		`;
		this.$img = root.querySelector('img');
		this.$wrap = root.querySelector('.wrap');
	}
	connectedCallback(){ 
		this.#sync();
		this.$wrap.addEventListener('click', this.#handleClick);
	}
	disconnectedCallback(){
		this.$wrap.removeEventListener('click', this.#handleClick);
	}
	attributeChangedCallback(){ this.#sync(); }
	#sync(){
		if(this.$img){
			const src = this.getAttribute('src') || '';
			this.$img.src = src;
			this.$img.alt = this.getAttribute('alt') || '';
			this.shadowRoot.querySelector('#maskImg')?.setAttribute('href', src);
		}
	}
	#handleClick = (e) => {
		const currentState = this.getAttribute('state');
		if(currentState !== 'small'){
			// Chuyển sang frame 2 (nhỏ)
			this.setAttribute('state', 'small');
			document.dispatchEvent(new CustomEvent('cc-board-clicked', {
				bubbles: true,
				detail: { board: this, state: 'small' }
			}));
		} else {
			// Đang ở frame 2 hoặc frame 3
			// Kiểm tra xem login panel có đang mở không
			const loginPanel = document.querySelector('cc-login-panel');
			if(loginPanel && loginPanel.getAttribute('visible') === 'true'){
				// Đang ở frame 3 → đóng login panel, quay lại frame 2
				loginPanel.setAttribute('visible', 'false');
			} else {
				// Đang ở frame 2 → quay lại frame 1
				this.removeAttribute('state');
				document.dispatchEvent(new CustomEvent('cc-board-reset', {
					bubbles: true,
					detail: { board: this, state: 'normal' }
				}));
			}
		}
	}
}
customElements.define('cc-board', CCBoard);